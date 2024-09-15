package org.openmrs.module.ipd.service.impl;

import org.apache.commons.lang.StringUtils;
import org.openmrs.Patient;
import org.openmrs.Visit;
import org.openmrs.api.context.Context;
import org.openmrs.module.fhir2.apiext.FhirMedicationAdministrationService;
import org.openmrs.module.fhir2.apiext.dao.FhirMedicationAdministrationDao;
import org.openmrs.module.fhir2.apiext.translators.MedicationAdministrationTranslator;
import org.openmrs.module.ipd.api.model.MedicationAdministration;
import org.openmrs.module.ipd.api.model.Schedule;
import org.openmrs.module.ipd.api.model.ServiceType;
import org.openmrs.module.ipd.api.model.Slot;
import org.openmrs.module.ipd.api.service.ScheduleService;
import org.openmrs.module.ipd.api.service.SlotService;
import org.openmrs.module.ipd.api.translators.MedicationAdministrationToSlotStatusTranslator;
import org.openmrs.module.ipd.api.util.DateTimeUtil;
import org.openmrs.module.ipd.contract.MedicationAdministrationRequest;
import org.openmrs.module.ipd.contract.ScheduleMedicationRequest;
import org.openmrs.module.ipd.factory.MedicationAdministrationFactory;
import org.openmrs.module.ipd.factory.ScheduleFactory;
import org.openmrs.module.ipd.factory.SlotFactory;
import org.openmrs.module.ipd.service.IPDMedicationAdministrationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;


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

    @Autowired
    public IPDMedicationAdministrationServiceImpl(FhirMedicationAdministrationService fhirMedicationAdministrationService,
                                                  MedicationAdministrationTranslator medicationAdministrationTranslator,
                                                  MedicationAdministrationFactory medicationAdministrationFactory,
                                                  SlotFactory slotFactory, SlotService slotService, ScheduleService scheduleService,
                                                  FhirMedicationAdministrationDao fhirMedicationAdministrationDao,
                                                  MedicationAdministrationToSlotStatusTranslator medicationAdministrationToSlotStatusTranslator,
                                                  ScheduleFactory scheduleFactory) {
        this.fhirMedicationAdministrationService = fhirMedicationAdministrationService;
        this.medicationAdministrationTranslator = medicationAdministrationTranslator;
        this.medicationAdministrationFactory = medicationAdministrationFactory;
        this.slotFactory = slotFactory;
        this.slotService = slotService;
        this.scheduleService = scheduleService;
        this.fhirMedicationAdministrationDao = fhirMedicationAdministrationDao;
        this.medicationAdministrationToSlotStatusTranslator=medicationAdministrationToSlotStatusTranslator;
        this.scheduleFactory = scheduleFactory;
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

}
