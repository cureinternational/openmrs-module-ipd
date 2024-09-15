package org.openmrs.module.ipd.api.dao.impl;

import org.hibernate.query.Query;
import org.openmrs.Concept;
import org.openmrs.Order;
import org.openmrs.Visit;
import org.openmrs.module.ipd.api.dao.SlotDAO;
import org.openmrs.module.ipd.api.model.Reference;
import org.openmrs.module.ipd.api.model.Slot;
import org.hibernate.SessionFactory;
import org.openmrs.api.db.DAOException;
import org.openmrs.module.ipd.api.util.DateTimeUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

public class HibernateSlotDAO implements SlotDAO {

	private static final Logger log = LoggerFactory.getLogger(HibernateSlotDAO.class);

	private SessionFactory sessionFactory;

	public void setSessionFactory(SessionFactory sessionFactory) {
		this.sessionFactory = sessionFactory;
	}

	@Override
	public Slot getSlot(Integer slotId) throws DAOException {
		return sessionFactory.getCurrentSession().get(Slot.class, slotId);
	}

	@Override
	public Slot getSlotByUUID(String uuid) throws DAOException {
		Slot s = (Slot)this.sessionFactory.getCurrentSession().createQuery("from Slot s where s.uuid = :uuid").setString("uuid", uuid).uniqueResult();
		return s;
	}

	@Override
	public Slot saveSlot(Slot slot) throws DAOException {
		sessionFactory.getCurrentSession().saveOrUpdate(slot);
		return slot;
	}

	@Override
	public List<Slot> getSlotsBySubjectReferenceIdAndForDateAndServiceType(Reference subject, LocalDate forDate, Concept serviceType) {
		Query query = sessionFactory.getCurrentSession()
				.createQuery("FROM Slot slot WHERE slot.schedule.subject=:subject and YEAR(slot.startDateTime)=:forYear and MONTH(slot.startDateTime)=:forMonth and DAY(slot.startDateTime)=:forDay and slot.serviceType=:serviceType and slot.voided=0");

		query.setParameter("subject", subject);
		query.setParameter("forYear", forDate.getYear());
		query.setParameter("forMonth", forDate.getMonthValue());
		query.setParameter("forDay", forDate.getDayOfMonth());
		query.setParameter("serviceType", serviceType);

		return query.getResultList();
	}

	@Override
	public List<Slot> getSlotsBySubjectReferenceIdAndServiceType(Reference subject, Concept serviceType) {
		Query query = sessionFactory.getCurrentSession()
				.createQuery("FROM Slot slot WHERE slot.schedule.subject=:subject and slot.serviceType=:serviceType and slot.voided=0");

		query.setParameter("subject", subject);
		query.setParameter("serviceType", serviceType);

		return query.getResultList();
	}

	@Override
	public List<Slot> getSlotsBySubjectReferenceIdAndServiceTypeAndOrderUuids(Reference subject, Concept serviceType, List<String> orderUuids) {
		Query query = sessionFactory.getCurrentSession()
				.createQuery("FROM Slot slot " +
						"WHERE slot.schedule.subject=:subject and " +
						"slot.serviceType=:serviceType and"
						+ " slot.order.uuid IN :orderUuids and "
				         + "slot.voided = 0");

		query.setParameter("subject", subject);
		query.setParameter("serviceType", serviceType);
		query.setParameter("orderUuids", orderUuids);

		return query.getResultList();
	}

	@Override
	public List<Slot> getSlotsByPatientAndVisitAndServiceType(Reference subject, Visit visit, Concept serviceType) {
		Query query = sessionFactory.getCurrentSession()
				.createQuery("FROM Slot slot WHERE slot.schedule.subject=:subject and slot.schedule.visit=:visit and slot.serviceType=:serviceType");

		query.setParameter("subject", subject);
		query.setParameter("visit", visit);
		query.setParameter("serviceType", serviceType);

		return query.getResultList();
	}
    @Override
    public List<Slot> getSlotsBySubjectReferenceIdAndForTheGivenTimeFrame(Reference subject, LocalDateTime localStartDate, LocalDateTime localEndDate, Visit visit, Concept serviceType){
        Query query = sessionFactory.getCurrentSession()
                .createQuery("FROM Slot slot WHERE slot.schedule.subject=:subject and ((slot.startDateTime BETWEEN :startDate and :endDate) or (slot.serviceType = :serviceType)) and slot.voided=0 and slot.schedule.visit=:visit");

        query.setParameter("subject", subject);
        query.setParameter("startDate", localStartDate);
        query.setParameter("endDate", localEndDate);
		query.setParameter("visit", visit);
		query.setParameter("serviceType", serviceType);

        return query.getResultList();
    }

	@Override
	public List<Slot> getSlotsBySubjectIncludingAdministeredTimeFrame(Reference subject, LocalDateTime localStartDate, LocalDateTime localEndDate, Visit visit) {
		Query query = sessionFactory.getCurrentSession()
				.createQuery("SELECT slot FROM Slot slot " +
						"LEFT JOIN slot.medicationAdministration medAdmin " +
						"WHERE (slot.schedule.subject = :subject) AND slot.voided = 0 AND slot.schedule.visit=:visit AND " +
						"(((slot.startDateTime BETWEEN :startDateTime AND :endDateTime) AND " +
						"(medAdmin.administeredDateTime BETWEEN :startDate AND :endDate or medAdmin is null)) OR " +
						"(medAdmin is not null AND " +
						"(medAdmin.administeredDateTime BETWEEN :startDate AND :endDate)))");

		query.setParameter("subject", subject);
		query.setParameter("visit", visit);
		query.setParameter("startDateTime", localStartDate);
		query.setParameter("endDateTime", localEndDate);
		query.setParameter("startDate", DateTimeUtil.convertLocalDateTimeDate(localStartDate));
		query.setParameter("endDate", DateTimeUtil.convertLocalDateTimeDate(localEndDate));

		return query.getResultList();
	}

	@Override
	public List<Slot> getSlotsForPatientListByTime(List<String> patientUuidList, LocalDateTime localStartDate, LocalDateTime localEndDate) {
		Query query = sessionFactory.getCurrentSession()
				.createQuery("SELECT slot FROM Slot slot \n" +
						"INNER JOIN slot.schedule.subject reference \n" +
						"INNER JOIN slot.schedule.visit visit \n" +
						"WHERE (slot.startDateTime BETWEEN :startDate and :endDate) \n" +
						"and slot.voided=0 \n" +
						"and visit.stopDatetime is NULL \n" +
						"and reference.type = 'org.openmrs.Patient' \n" +
						"and reference.targetUuid in (:patientUuidList)");

		query.setParameterList("patientUuidList", patientUuidList);
		query.setParameter("startDate", localStartDate);
		query.setParameter("endDate", localEndDate);

		return query.getResultList();
	}

	@Override
	public List<Slot> getImmediatePreviousSlotsForPatientListByTime(List<String> patientUuidList, LocalDateTime localStartDate) {
		String maxDateTimeSubquery = "SELECT s.order, MAX(s.startDateTime) AS maxStartDateTime " +
				"FROM Slot s " +
				"INNER JOIN s.schedule.subject reference " +
				"INNER JOIN s.schedule.visit visit " +
				"WHERE s.startDateTime < :startDate " +
				"AND s.voided = 0 " +
				"AND visit.stopDatetime IS NULL " +
				"AND reference.type = 'org.openmrs.Patient' " +
				"AND reference.targetUuid IN (:patientUuidList) " +
				"GROUP BY s.order";

		String latestPreviousSlotsQuery = "SELECT slot " +
				"FROM Slot slot " +
				"INNER JOIN slot.schedule.subject reference " +
				"INNER JOIN slot.schedule.visit visit " +
				"WHERE slot.startDateTime < :startDate " +
				"AND slot.voided = 0 " +
				"AND visit.stopDatetime IS NULL " +
				"AND reference.type = 'org.openmrs.Patient' " +
				"AND reference.targetUuid IN (:patientUuidList) " +
				"AND (slot.order, slot.startDateTime) IN " +
				"( " + maxDateTimeSubquery + " ) ";

		Query query = sessionFactory.getCurrentSession()
				.createQuery(latestPreviousSlotsQuery);

		query.setParameterList("patientUuidList", patientUuidList);
		query.setParameter("startDate", localStartDate);

		return query.getResultList();
	}

	@Override
	public List<Object[]> getSlotDurationForPatientsByOrder(List<Order> orders, List<Concept> serviceTypes) {
		Query query = sessionFactory.getCurrentSession()
				.createQuery("SELECT \n" +
						"    slot.order AS order,\n" +
						"    MIN(slot.startDateTime) AS minStartDateTime,\n" +
						"    MAX(slot.startDateTime) AS maxStartDateTime\n" +
						"FROM\n" +
						"    Slot slot\n" +
						"INNER JOIN\n" +
						"    slot.schedule.subject reference\n" +
						"INNER JOIN\n" +
						"	 slot.schedule.visit visit \n" +
						"WHERE\n" +
						"    slot.voided = 0\n" +
						"    AND slot.serviceType IN (:serviceTypes)\n" +
						"	 AND visit.stopDatetime is NULL \n" +
						"    AND slot.order IN (:orders)\n" +
						"GROUP BY\n" +
						"    slot.order");

		query.setParameterList("orders", orders);
		query.setParameterList("serviceTypes", serviceTypes);
		return query.getResultList();
	}

	@Override
	public List<Slot> getLastSlotForAnOrder(LocalDateTime localStartDateTime){
		Query query = sessionFactory.getCurrentSession()
				.createQuery("SELECT s from Slot s\n" +
						"where s.startDateTime IN (SELECT MAX(s1.startDateTime) \n" +
						"from Slot s1 WHERE \n" +
						"s1.startDateTime>:previousDate \n" +
						"and s1.startDateTime<=:currentDate \n" +
						"and s1.order IS NOT NULL \n" +
						"and s1.order = s.order \n" +
						"and s1.voided = 0 \n" +
						"GROUP by s1.order \n) " +
						"and s.voided = 0 \n" +
						"GROUP by s.order");

        query.setParameter("currentDate", localStartDateTime);
        query.setParameter("previousDate", localStartDateTime.minus(1, ChronoUnit.DAYS));
		return query.getResultList();
	}

	@Override
	public List<Slot>  getScheduledSlots(List<Order> orders){
		Query query = sessionFactory.getCurrentSession()
				.createQuery("From Slot slot WHERE slot.order IN (:order) and slot.status=:status and slot.voided=0");

		query.setParameter("order", orders);
		query.setParameter("status", Slot.SlotStatus.SCHEDULED);
		return query.getResultList();
	}
}
