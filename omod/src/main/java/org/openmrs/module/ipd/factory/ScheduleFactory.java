package org.openmrs.module.ipd.factory;

import org.openmrs.Concept;
import org.openmrs.Patient;
import org.openmrs.Provider;
import org.openmrs.Visit;
import org.openmrs.api.ConceptService;
import org.openmrs.module.ipd.api.model.Reference;
import org.openmrs.module.ipd.api.model.Schedule;
import org.openmrs.module.ipd.api.service.ReferenceService;
import org.openmrs.module.ipd.contract.ScheduleMedicationRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Optional;

import static org.openmrs.module.ipd.api.model.ServiceType.MEDICATION_REQUEST;
import static org.openmrs.module.ipd.api.util.DateTimeUtil.convertDateToLocalDateTime;

@Component
public class ScheduleFactory {

    private final ConceptService conceptService;
    private final ReferenceService referenceService;

    @Autowired
    public ScheduleFactory(ConceptService conceptService, ReferenceService referenceService) {
        this.conceptService = conceptService;
        this.referenceService = referenceService;
    }

    public Schedule createScheduleForMedicationFrom(ScheduleMedicationRequest request, Visit visit) {
        Schedule schedule = new Schedule();
        Concept medicationRequestServiceType = conceptService.getConceptByName(MEDICATION_REQUEST.conceptName());

        Reference subject = getReference(Patient.class.getTypeName(), request.getPatientUuid());
        Reference actor = getReference(Provider.class.getTypeName(), request.getProviderUuid());

        schedule.setSubject(subject);
        schedule.setActor(actor);
        schedule.setStartDate(convertDateToLocalDateTime(visit.getStartDatetime()));
//        schedule.setEndDate(convertDateToLocalDateTime(null);

        schedule.setServiceType(medicationRequestServiceType);
        schedule.setVisit(visit);
        schedule.setActive(true);

        return schedule;
    }

    private Reference getReference(String type, String targetUuid) {
        Optional<Reference> reference = referenceService.getReferenceByTypeAndTargetUUID(type, targetUuid);
        return reference.orElseGet(() -> new Reference(type, targetUuid));
    }
}
