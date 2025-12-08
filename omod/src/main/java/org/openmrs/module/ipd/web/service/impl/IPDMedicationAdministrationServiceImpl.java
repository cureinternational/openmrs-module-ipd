package org.openmrs.module.ipd.web.service.impl;

import org.apache.commons.lang.StringUtils;
import org.openmrs.Patient;
import org.openmrs.Visit;
import org.openmrs.Provider;
import org.openmrs.api.context.Context;
import org.openmrs.module.fhir2.apiext.FhirMedicationAdministrationService;
import org.openmrs.module.fhir2.apiext.dao.FhirMedicationAdministrationDao;
import org.openmrs.module.fhir2.apiext.dao.FhirMedicationAdministrationNoteDao;
import org.openmrs.module.fhir2.apiext.translators.MedicationAdministrationTranslator;
import org.openmrs.module.ipd.api.model.ApprovalStatus;
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
import org.openmrs.module.ipd.web.contract.NoteAmendmentRequest;
import org.openmrs.module.ipd.web.contract.NoteAcknowledgeRequest;
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
import java.util.Date;
import java.util.List;
import java.util.Objects;

import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;


@Transactional
@Service
public class IPDMedicationAdministrationServiceImpl implements IPDMedicationAdministrationService {


    private FhirMedicationAdministrationService fhirMedicationAdministrationService;
    private MedicationAdministrationTranslator medicationAdministrationTranslator;
    private MedicationAdministrationFactory medicationAdministrationFactory;
    private SlotFactory slotFactory;
    private SlotService slotService;
    private ScheduleService scheduleService;
    private FhirMedicationAdministrationDao fhirMedicationAdministrationDao;
    private MedicationAdministrationToSlotStatusTranslator medicationAdministrationToSlotStatusTranslator;
    private ScheduleFactory scheduleFactory;
    private FhirMedicationAdministrationNoteDao fhirMedicationAdministrationNoteDao;

    @Autowired
    public IPDMedicationAdministrationServiceImpl(FhirMedicationAdministrationService fhirMedicationAdministrationService,
                                                  MedicationAdministrationTranslator medicationAdministrationTranslator,
                                                  MedicationAdministrationFactory medicationAdministrationFactory,
                                                  SlotFactory slotFactory, SlotService slotService, ScheduleService scheduleService,
                                                  FhirMedicationAdministrationDao fhirMedicationAdministrationDao,
                                                  MedicationAdministrationToSlotStatusTranslator medicationAdministrationToSlotStatusTranslator,
                                                  ScheduleFactory scheduleFactory,
                                                  FhirMedicationAdministrationNoteDao fhirMedicationAdministrationNoteDao) {
        this.fhirMedicationAdministrationService = fhirMedicationAdministrationService;
        this.medicationAdministrationTranslator = medicationAdministrationTranslator;
        this.medicationAdministrationFactory = medicationAdministrationFactory;
        this.slotFactory = slotFactory;
        this.slotService = slotService;
        this.scheduleService = scheduleService;
        this.fhirMedicationAdministrationDao = fhirMedicationAdministrationDao;
        this.medicationAdministrationToSlotStatusTranslator=medicationAdministrationToSlotStatusTranslator;
        this.scheduleFactory = scheduleFactory;
        this.fhirMedicationAdministrationNoteDao = fhirMedicationAdministrationNoteDao;
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
    public MedicationAdministrationNote amendNote(
            String noteUuid,
            NoteAmendmentRequest amendmentRequest) {

        MedicationAdministrationNote note = fhirMedicationAdministrationNoteDao.get(noteUuid);
        if (isNull(note)) {
            throw new RuntimeException("Note not found with UUID: " + noteUuid);
        }
        String userUuid = Context.getUserContext().getAuthenticatedUser().getUuid();
        Provider provider = Context.getProviderService().getProviderByUuid(userUuid);

        if (nonNull(provider)) {
            note.setAmendedBy(provider);
        } else {
            throw new RuntimeException("Provider not found with UUID: " + userUuid);
        }

        note.setAmendedText(amendmentRequest.getAmendedText());
        note.setAmendedTime(new Date());
        note.setAmendedReason(amendmentRequest.getAmendedReason());
        note.setApprovalStatus(ApprovalStatus.PENDING);

        return fhirMedicationAdministrationNoteDao.createOrUpdate(note);
    }

    @Override
    public MedicationAdministrationNote acknowledgeAmendment(
            String noteUuid,
            NoteAcknowledgeRequest acknowledgeRequest) {

        MedicationAdministrationNote amendmentNote = fhirMedicationAdministrationNoteDao.get(noteUuid);
        if (isNull(amendmentNote)) {
            throw new RuntimeException("Amendment note not found with UUID: " + noteUuid);
        }

        if (isNull(amendmentNote.getAmendedReason())) {
            throw new RuntimeException("Note is not an amendment note");
        }

        if (nonNull(amendmentNote.getApprovalStatus()) && amendmentNote.getApprovalStatus() != ApprovalStatus.PENDING) {
            throw new RuntimeException("Amendment note has already been acknowledged");
        }

        if (isNull(acknowledgeRequest.getApprovalStatus()) || acknowledgeRequest.getApprovalStatus().trim().isEmpty()) {
            throw new RuntimeException("Approval status is required when acknowledging an amendment");
        }

        if (nonNull(acknowledgeRequest.getApprovedByUuid())) {
            Provider provider = Context.getProviderService().getProviderByUuid(acknowledgeRequest.getApprovedByUuid());
            if (nonNull(provider)) {
                amendmentNote.setApprovedBy(provider);
            } else {
                throw new RuntimeException("Provider not found with UUID: " + acknowledgeRequest.getApprovedByUuid());
            }
        }
        amendmentNote.setApprovalStatus(ApprovalStatus.valueOf(acknowledgeRequest.getApprovalStatus().toUpperCase()));
        amendmentNote.setApprovedDateTime(acknowledgeRequest.getApprovedDateTimeAsLocaltime());
        amendmentNote.setApprovalNotes(acknowledgeRequest.getApprovalNotes());

        return fhirMedicationAdministrationNoteDao.createOrUpdate(amendmentNote);
    }

}
