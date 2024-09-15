package org.openmrs.module.ipd.api.dao.impl;

import org.hibernate.SessionFactory;
import org.hibernate.query.Query;
import org.openmrs.Visit;
import org.openmrs.api.db.DAOException;
import org.openmrs.module.ipd.api.dao.ScheduleDAO;
import org.openmrs.module.ipd.api.model.Schedule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

public class HibernateScheduleDAO implements ScheduleDAO {

	private static final Logger log = LoggerFactory.getLogger(HibernateScheduleDAO.class);
	private SessionFactory sessionFactory;

	public void setSessionFactory(SessionFactory sessionFactory) {
		this.sessionFactory = sessionFactory;
	}

	@Override
	public Schedule getSchedule(Integer scheduleId) throws DAOException {
		return sessionFactory.getCurrentSession().get(Schedule.class, scheduleId);
	}

	@Override
	public Schedule saveSchedule(Schedule schedule) throws DAOException {
		sessionFactory.getCurrentSession().saveOrUpdate(schedule);
		return schedule;
	}

    @Override
    public Schedule getScheduleByVisit(Visit visit) throws DAOException {
		Query query = sessionFactory.getCurrentSession()
				.createQuery("FROM Schedule schedule " +
						"WHERE schedule.visit = :visit " +
						"and active = 1");

		query.setParameter("visit", visit);

		return (Schedule) query.uniqueResult();
    }
}
