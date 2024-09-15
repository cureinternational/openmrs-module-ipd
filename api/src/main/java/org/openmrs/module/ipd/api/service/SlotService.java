package org.openmrs.module.ipd.api.service;

import org.openmrs.Concept;
import org.openmrs.Order;
import org.openmrs.Visit;
import org.openmrs.module.ipd.api.model.Reference;
import org.openmrs.module.ipd.api.model.Slot;
import org.openmrs.api.APIException;
import org.openmrs.api.OpenmrsService;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;


public interface SlotService extends OpenmrsService {

//	@Authorized({ PrivilegeConstants.EDIT_IPD_SLOTS })
	Slot getSlot(Integer slotId) throws APIException;

	//	@Authorized({ PrivilegeConstants.EDIT_IPD_SLOTS })
	Slot getSlotByUUID(String uuid) throws APIException;

//	@Authorized({ PrivilegeConstants.EDIT_IPD_SLOTS })
	Slot saveSlot(Slot slot) throws APIException;

	List<Slot> getSlotsBySubjectReferenceIdAndForDateAndServiceType(Reference subject, LocalDate forDate, Concept serviceType);

	List<Slot> getSlotsBySubjectReferenceIdAndServiceType(Reference subject, Concept serviceType);

	List<Slot> getSlotsBySubjectReferenceIdAndServiceTypeAndOrderUuids(Reference subject, Concept serviceType, List<String> orderUuids);

	void voidSlot(Slot slot,String voidReason);

	List<Slot> getSlotsByPatientAndVisitAndServiceType(Reference subject, Visit visit, Concept serviceType);

	List<Slot> getSlotsBySubjectReferenceIdAndForTheGivenTimeFrame(Reference reference, LocalDateTime localStartDate, LocalDateTime localEndDate, Visit visit);

	List<Slot> getSlotsBySubjectReferenceIncludingAdministeredTimeFrame(Reference subject, LocalDateTime localStartDate, LocalDateTime localEndDate, Visit visit);

	List<Slot> getSlotsForPatientListByTime(List<String> patientUuidList, LocalDateTime localStartDate, LocalDateTime localEndDate);

	List<Slot> getImmediatePreviousSlotsForPatientListByTime(List<String> patientUuidList, LocalDateTime localStartDate);

	List<Object[]> getSlotDurationForPatientsByOrder(List<Order> orders, List<Concept> serviceTypes);


    List<Slot> getLastSlotForAnOrder(LocalDateTime currentDay);

	List<Slot> getScheduledSlots(List<Order> orders);

	void markSlotsAsMissed(List<Slot> markSlotStatusAsMissed, Map<Order, LocalDateTime> slotsForADay);
}
