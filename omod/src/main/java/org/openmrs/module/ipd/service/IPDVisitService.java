package org.openmrs.module.ipd.service;

import org.openmrs.module.ipd.model.IPDDrugOrder;
import org.openmrs.module.ipd.api.model.ServiceType;
import org.openmrs.module.ipd.api.model.Slot;

import java.util.Date;
import java.util.List;

public interface IPDVisitService {

    List<IPDDrugOrder> getPrescribedOrders(String visitUuid, Boolean includeActiveVisit, Integer numberOfVisits, Date startDate, Date endDate, Boolean getEffectiveOrdersOnly);
    List<Slot> getMedicationSlots(String visitUuid, ServiceType serviceType);
}
