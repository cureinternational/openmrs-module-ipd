package org.openmrs.module.ipd.web.service.impl;

import org.apache.commons.lang.StringUtils;
import org.openmrs.Concept;
import org.openmrs.Patient;
import org.openmrs.Provider;
import org.openmrs.Visit;
import org.openmrs.api.APIException;
import org.openmrs.api.context.Context;
import org.openmrs.module.fhir2.apiext.FhirMedicationAdministrationService;
import org.openmrs.module.fhir2.apiext.dao.FhirMedicationAdministrationDao;
import org.openmrs.module.fhir2.apiext.translators.MedicationAdministrationTranslator;
import org.openmrs.module.fhirExtension.model.Task;
import org.openmrs.module.fhirExtension.service.TaskService;
import org.openmrs.module.ipd.web.util.AcknowledgementTaskUtil;
import org.openmrs.module.ipd.web.contract.MedicationAdministrationAcknowledgementRequest;
import org.openmrs.module.ipd.web.contract.MedicationAdministrationNoteRequest;
import org.openmrs.module.ipd.api.model.MedicationAdministration;
import org.openmrs.module.ipd.api.model.MedicationAdministrationNote;
import org.openmrs.module.ipd.api.model.Schedule;
import org.openmrs.module.ipd.api.model.ServiceType;
import org.openmrs.module.ipd.api.model.Slot;
import org.openmrs.module.ipd.api.service.ScheduleService;
import org.openmrs.module.ipd.api.service.SlotService;
import org.openmrs.module.ipd.api.translators.MedicationAdministrationToSlotStatusTranslator;
import org.openmrs.module.ipd.api.util.DateTimeUtil;
import org.openmrs.module.ipd.web.contract.MedicationAdministrationRequest;
import org.openmrs.module.ipd.web.contract.ScheduleMedicationRequest;
import org.openmrs.module.ipd.web.factory.MedicationAdministrationFactory;
import org.openmrs.module.ipd.web.factory.ScheduleFactory;
import org.openmrs.module.ipd.web.factory.SlotFactory;
import org.openmrs.module.ipd.web.mapper.AcknowledgementTaskMapper;
import org.openmrs.module.ipd.web.service.IPDMedicationAdministrationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;


@Transactional
@Service
public class IPDMedicationAdministrationServiceImpl implements IPDMedicationAdministrationService {

    private static final String ACKNOWLEDGE_TASK_NAME = AcknowledgementTaskUtil.ACKNOWLEDGE_TASK_NAME;
    private static final String ACKNOWLEDGEMENT_TASK_TYPE_PROPERTY = "ipd.acknowledgement_task_type";

    // Per-administration locks so concurrent acknowledge requests for the same administration
    // serialize on the check-then-create critical section instead of racing past isLocked().
    private static final ConcurrentHashMap<String, Object> ACKNOWLEDGEMENT_LOCKS = new ConcurrentHashMap<>();

    private FhirMedicationAdministrationService fhirMedicationAdministrationService;
    private MedicationAdministrationTranslator medicationAdministrationTranslator;
    private MedicationAdministrationFactory medicationAdministrationFactory;
    private SlotFactory slotFactory;
    private SlotService slotService;
    private ScheduleService scheduleService;
    private FhirMedicationAdministrationDao fhirMedicationAdministrationDao;
    private MedicationAdministrationToSlotStatusTranslator medicationAdministrationToSlotStatusTranslator;
    private ScheduleFactory scheduleFactory;
    private TaskService taskService;
    private AcknowledgementTaskMapper acknowledgementTaskMapper;

    @Autowired
    public IPDMedicationAdministrationServiceImpl(FhirMedicationAdministrationService fhirMedicationAdministrationService,
                                                  MedicationAdministrationTranslator medicationAdministrationTranslator,
                                                  MedicationAdministrationFactory medicationAdministrationFactory,
                                                  SlotFactory slotFactory, SlotService slotService, ScheduleService scheduleService,
                                                  FhirMedicationAdministrationDao fhirMedicationAdministrationDao,
                                                  MedicationAdministrationToSlotStatusTranslator medicationAdministrationToSlotStatusTranslator,
                                                  ScheduleFactory scheduleFactory,
                                                  TaskService taskService,
                                                  AcknowledgementTaskMapper acknowledgementTaskMapper) {
        this.fhirMedicationAdministrationService = fhirMedicationAdministrationService;
        this.medicationAdministrationTranslator = medicationAdministrationTranslator;
        this.medicationAdministrationFactory = medicationAdministrationFactory;
        this.slotFactory = slotFactory;
        this.slotService = slotService;
        this.scheduleService = scheduleService;
        this.fhirMedicationAdministrationDao = fhirMedicationAdministrationDao;
        this.medicationAdministrationToSlotStatusTranslator=medicationAdministrationToSlotStatusTranslator;
        this.scheduleFactory = scheduleFactory;
        this.taskService = taskService;
        this.acknowledgementTaskMapper = acknowledgementTaskMapper;
    }

    private org.hl7.fhir.r4.model.MedicationAdministration createMedicationAdministration(MedicationAdministrationRequest medicationAdministrationRequest) {
        MedicationAdministration medicationAdministration = medicationAdministrationFactory.mapRequestToMedicationAdministration(medicationAdministrationRequest, new MedicationAdministration());
        return fhirMedicationAdministrationService.create(medicationAdministrationTranslator.toFhirResource(medicationAdministration));
    }

    @Override
    public org.hl7.fhir.r4.model.MedicationAdministration saveScheduledMedicationAdministration(MedicationAdministrationRequest medicationAdministrationRequest) {
        Slot slot = slotService.getSlotByUUID(medicationAdministrationRequest.getSlotUuid());
        if (slot == null) {
            throw new RuntimeException("Slot not found");
        } else {
            if (slot.getMedicationAdministration() != null) {
                return fhirMedicationAdministrationService.get(slot.getMedicationAdministration().getUuid());
            }
            if (!StringUtils.isBlank(medicationAdministrationRequest.getUuid())) {
                return fhirMedicationAdministrationService.get(medicationAdministrationRequest.getUuid());
            }
            org.hl7.fhir.r4.model.MedicationAdministration medicationAdministration = createMedicationAdministration(medicationAdministrationRequest);
            slot.setStatus(medicationAdministrationToSlotStatusTranslator.toSlotStatus(medicationAdministration.getStatus()));
            slot.setMedicationAdministration((MedicationAdministration) fhirMedicationAdministrationDao.get(medicationAdministration.getId()));
            slotService.saveSlot(slot);
            return medicationAdministration;
        }
    }

    @Override
    public org.hl7.fhir.r4.model.MedicationAdministration updateAdhocMedicationAdministration(String uuid, MedicationAdministrationRequest medicationAdministrationRequest) {
        MedicationAdministration medicationAdministration = medicationAdministrationFactory.mapRequestToMedicationAdministration(medicationAdministrationRequest,
                                                                                            (MedicationAdministration) fhirMedicationAdministrationDao.get(uuid));
        return fhirMedicationAdministrationService.update(uuid,medicationAdministrationTranslator.toFhirResource(medicationAdministration));
    }

    @Override
    public org.hl7.fhir.r4.model.MedicationAdministration saveAdhocMedicationAdministration(MedicationAdministrationRequest medicationAdministrationRequest) {
        Patient patient = Context.getPatientService().getPatientByUuid(medicationAdministrationRequest.getPatientUuid());
        Visit visit = Context.getVisitService().getActiveVisitsByPatient(patient).get(0);
        Schedule schedule = scheduleService.getScheduleByVisit(visit);
        if (schedule == null) {
            ScheduleMedicationRequest scheduleMedicationRequest = new ScheduleMedicationRequest();
            scheduleMedicationRequest.setPatientUuid(medicationAdministrationRequest.getPatientUuid());
            scheduleMedicationRequest.setProviderUuid(medicationAdministrationRequest.getProviders().get(0).getProviderUuid());
            schedule = scheduleService.saveSchedule(scheduleFactory.createScheduleForMedicationFrom(scheduleMedicationRequest, visit));
        }
        org.hl7.fhir.r4.model.MedicationAdministration medicationAdministration = createMedicationAdministration(medicationAdministrationRequest);
        MedicationAdministration openmrsMedicationAdministration = (MedicationAdministration) fhirMedicationAdministrationDao.get(medicationAdministration.getId());
        List<LocalDateTime> slotsStartTime = new ArrayList<>();
        slotsStartTime.add(DateTimeUtil.convertEpocUTCToLocalTimeZone(medicationAdministrationRequest.getAdministeredDateTime()));
        ServiceType serviceType = openmrsMedicationAdministration.getDrugOrder() == null ? ServiceType.EMERGENCY_MEDICATION_REQUEST : ServiceType.AS_NEEDED_MEDICATION_REQUEST;
        slotFactory.createSlotsForMedicationFrom(schedule, slotsStartTime, openmrsMedicationAdministration.getDrugOrder(),
                        openmrsMedicationAdministration, Slot.SlotStatus.COMPLETED, serviceType,"",null)
                .forEach(slotService::saveSlot);
        return medicationAdministration;
    }

    @Override
    public MedicationAdministrationNote amendNote(String medicationAdministrationUuid,
                                                    MedicationAdministrationNoteRequest noteRequest) {
        MedicationAdministration medicationAdministration = (MedicationAdministration) fhirMedicationAdministrationDao.get(medicationAdministrationUuid);
        if (medicationAdministration == null) {
            throw new APIException("MedicationAdministration not found with UUID: " + medicationAdministrationUuid);
        }
        if (isLocked(medicationAdministration)) {
            throw new APIException("Cannot amend note: Medication administration is acknowledged and locked.");
        }

        MedicationAdministrationNote previousNote = getLatestNote(medicationAdministration);
        MedicationAdministrationNote newNote = new MedicationAdministrationNote();
        newNote.setUuid(UUID.randomUUID().toString());
        newNote.setText(noteRequest.getText());
        newNote.setRecordedTime(noteRequest.getRecordedTimeAsLocaltime());

        if (noteRequest.getStatusReasonUuid() != null) {
            Concept statusReasonConcept = Context.getConceptService().getConceptByUuid(noteRequest.getStatusReasonUuid());
            if (statusReasonConcept == null) {
                throw new APIException("Amendment reason concept not found with UUID: " + noteRequest.getStatusReasonUuid());
            }
            newNote.setStatusReason(statusReasonConcept);
        }

        newNote.setPreviousNote(previousNote);

        Provider provider = Context.getProviderService().getProviderByUuid(noteRequest.getAuthorUuid());
        if (provider == null) {
            throw new APIException("Provider not found with UUID: " + noteRequest.getAuthorUuid());
        }
        newNote.setAuthor(provider);
        if (medicationAdministration.getNotes() == null) {
            medicationAdministration.setNotes(new java.util.HashSet<>());
        }
        medicationAdministration.getNotes().add(newNote);
        fhirMedicationAdministrationDao.createOrUpdate(medicationAdministration);

        return newNote;
    }

    @Override
    public Task acknowledge(String medicationAdministrationUuid,
                            MedicationAdministrationAcknowledgementRequest acknowledgementRequest) {
        MedicationAdministration medicationAdministration = (MedicationAdministration) fhirMedicationAdministrationDao.get(medicationAdministrationUuid);
        if (medicationAdministration == null) {
            throw new APIException("MedicationAdministration not found with UUID: " + medicationAdministrationUuid);
        }
        Object lock = ACKNOWLEDGEMENT_LOCKS.computeIfAbsent(medicationAdministrationUuid, uuid -> new Object());
        synchronized (lock) {
            try {
                if (isLocked(medicationAdministration)) {
                    throw new APIException("Medication administration is already acknowledged and cannot be acknowledged again.");
                }

                MedicationAdministrationNote latestNote = getLatestNote(medicationAdministration);
                if (latestNote == null) {
                    throw new APIException("No notes found to acknowledge for this medication administration.");
                }

                String taskTypeUuid = Context.getAdministrationService().getGlobalProperty(ACKNOWLEDGEMENT_TASK_TYPE_PROPERTY);
                if (taskTypeUuid == null || taskTypeUuid.isEmpty()) {
                    throw new APIException("Acknowledgement task type is not configured. Please set the global property: " + ACKNOWLEDGEMENT_TASK_TYPE_PROPERTY);
                }
                Concept taskTypeConcept = Context.getConceptService().getConceptByUuid(taskTypeUuid);
                if (taskTypeConcept == null) {
                    throw new APIException("Could not find a concept for the configured acknowledgement task type UUID: " + taskTypeUuid);
                }
                String taskType = taskTypeConcept.getName().getName();

                String patientUuid = medicationAdministration.getPatient() != null
                        ? medicationAdministration.getPatient().getUuid()
                        : null;

                Provider approver = getCurrentProvider();

                Task task = acknowledgementTaskMapper.createAcknowledgementTask(
                        latestNote.getUuid(),
                        patientUuid,
                        ACKNOWLEDGE_TASK_NAME,
                        taskType,
                        acknowledgementRequest.getRemarks(),
                        approver.getUuid()
                );
                taskService.saveTask(task);
                return task;
            } finally {
                ACKNOWLEDGEMENT_LOCKS.remove(medicationAdministrationUuid, lock);
            }
        }
    }

    private Provider getCurrentProvider() {
        java.util.Collection<Provider> providers = Context.getProviderService()
                .getProvidersByPerson(Context.getAuthenticatedUser().getPerson());
        if (providers == null || providers.isEmpty()) {
            throw new APIException("No provider account found for the authenticated user.");
        }
        return providers.iterator().next();
    }

    private boolean isLocked(MedicationAdministration medicationAdministration) {
        Set<MedicationAdministrationNote> notes = medicationAdministration.getNotes();
        if (notes == null || notes.isEmpty()) {
            return false;
        }

        Set<String> noteUuids = notes.stream()
                .map(MedicationAdministrationNote::getUuid)
                .collect(java.util.stream.Collectors.toSet());
        return AcknowledgementTaskUtil.isAnyNoteAcknowledged(taskService, noteUuids);
    }

    private MedicationAdministrationNote getLatestNote(MedicationAdministration medicationAdministration) {
        Set<MedicationAdministrationNote> notes = medicationAdministration.getNotes();
        if (notes == null || notes.isEmpty()) {
            return null;
        }

        List<MedicationAdministrationNote> activeNotes = notes.stream()
                .filter(note -> !Boolean.TRUE.equals(note.getVoided()))
                .collect(java.util.stream.Collectors.toList());

        Set<String> referencedAsPreviousUuids = activeNotes.stream()
                .map(MedicationAdministrationNote::getPreviousNote)
                .filter(java.util.Objects::nonNull)
                .map(MedicationAdministrationNote::getUuid)
                .collect(java.util.stream.Collectors.toSet());

        return activeNotes.stream()
                .filter(note -> !referencedAsPreviousUuids.contains(note.getUuid()))
                .max(Comparator.comparing(MedicationAdministrationNote::getDateCreated,
                        Comparator.nullsFirst(Comparator.naturalOrder())))
                .orElse(null);
    }
}
