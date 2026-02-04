package org.openmrs.module.ipd.web.service.impl;

import org.apache.commons.lang.StringUtils;
import org.openmrs.Patient;
import org.openmrs.Provider;
import org.openmrs.Visit;
import org.openmrs.api.APIException;
import org.openmrs.api.context.Context;
import org.openmrs.module.fhir2.apiext.FhirMedicationAdministrationService;
import org.openmrs.module.fhir2.apiext.dao.FhirMedicationAdministrationDao;
import org.openmrs.module.fhir2.apiext.translators.MedicationAdministrationTranslator;
import org.openmrs.module.fhir2.model.FhirTask;
import org.openmrs.module.fhirExtension.model.Task;
import org.openmrs.module.fhirExtension.model.TaskSearchRequest;
import org.openmrs.module.fhirExtension.service.TaskService;
import org.openmrs.module.ipd.web.contract.MedicationAdministrationAcknowledgementRequest;
import org.openmrs.module.ipd.web.contract.MedicationAdministrationNoteRequest;
import org.openmrs.module.ipd.web.mapper.AcknowledgementTaskMapper;
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


@Transactional
@Service
public class IPDMedicationAdministrationServiceImpl implements IPDMedicationAdministrationService {

    private static final String ACKNOWLEDGE_TASK_NAME = "ACKNOWLEDGE_MEDICATION_NOTE";

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
                        openmrsMedicationAdministration, Slot.SlotStatus.COMPLETED, serviceType,"")
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
        newNote.setAmendmentReason(noteRequest.getReason());
        newNote.setPreviousNote(previousNote);
        newNote.setRecordedTime(noteRequest.getRecordedTimeAsLocaltime());

        Provider provider = Context.getProviderService().getProviderByUuid(noteRequest.getAuthorUuid());
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
        if (isLocked(medicationAdministration)) {
            throw new APIException("Medication administration is already acknowledged and cannot be acknowledged again.");
        }

        MedicationAdministrationNote latestNote = getLatestNote(medicationAdministration);
        if (latestNote == null) {
            throw new APIException("No notes found to acknowledge for this medication administration.");
        }

        Provider provider = Context.getProviderService().getProviderByUuid(acknowledgementRequest.getApprovedByUuid());
        String encounterUuid = medicationAdministration.getEncounter() != null ? medicationAdministration.getEncounter().getUuid() : null;
        Task task = acknowledgementTaskMapper.createAcknowledgementTask(
                latestNote.getUuid(),
                encounterUuid,
                ACKNOWLEDGE_TASK_NAME,
                acknowledgementRequest.getRemarks(),
                acknowledgementRequest.getApprovedByUuid()
        );

        taskService.saveTask(task);
        return task;
    }

    private boolean isLocked(MedicationAdministration medicationAdministration) {
        Set<MedicationAdministrationNote> notes = medicationAdministration.getNotes();
        if (notes == null || notes.isEmpty()) {
            return false;
        }

        TaskSearchRequest searchRequest = new TaskSearchRequest();
        searchRequest.setTaskName(java.util.Arrays.asList(ACKNOWLEDGE_TASK_NAME));
        searchRequest.setTaskStatus(java.util.Arrays.asList(FhirTask.TaskStatus.COMPLETED));

        List<Task> acknowledgementTasks = taskService.searchTasks(searchRequest);
        for (MedicationAdministrationNote note : notes) {
            for (Task task : acknowledgementTasks) {
                if (isTaskForNote(task, note.getUuid())) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean isTaskForNote(Task task, String noteUuid) {
        if (task == null || task.getFhirTask() == null) {
            return false;
        }

        FhirTask fhirTask = task.getFhirTask();
        if (fhirTask.getForReference() != null &&
                fhirTask.getForReference().getTargetUuid() != null &&
                fhirTask.getForReference().getTargetUuid().equals(noteUuid)) {
            return true;
        }

        return false;
    }

    private MedicationAdministrationNote getLatestNote(MedicationAdministration medicationAdministration) {
        Set<MedicationAdministrationNote> notes = medicationAdministration.getNotes();
        if (notes == null || notes.isEmpty()) {
            return null;
        }

        return notes.stream()
                .filter(note -> !note.getVoided())
                .max(Comparator.comparing(MedicationAdministrationNote::getDateCreated))
                .orElse(null);
    }
}
