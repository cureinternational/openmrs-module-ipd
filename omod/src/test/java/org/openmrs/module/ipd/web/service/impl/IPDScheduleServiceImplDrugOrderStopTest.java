package org.openmrs.module.ipd.web.service.impl;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.openmrs.Concept;
import org.openmrs.api.AdministrationService;
import org.openmrs.api.ConceptService;
import org.openmrs.api.context.Context;
import org.openmrs.module.emrapi.encounter.domain.EncounterTransaction;
import org.openmrs.module.ipd.api.events.IPDEventManager;
import org.openmrs.module.ipd.api.model.MedicationAdministration;
import org.openmrs.module.ipd.api.model.Reference;
import org.openmrs.module.ipd.api.model.ServiceType;
import org.openmrs.module.ipd.api.model.Slot;
import org.openmrs.module.ipd.api.service.ReferenceService;
import org.openmrs.module.ipd.api.service.SlotService;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@RunWith(PowerMockRunner.class)
@PrepareForTest({Context.class})
public class IPDScheduleServiceImplDrugOrderStopTest {

    @InjectMocks
    private IPDScheduleServiceImpl service;

    @Mock private SlotService slotService;
    @Mock private ConceptService conceptService;
    @Mock private ReferenceService referenceService;
    @Mock private AdministrationService administrationService;
    @Mock private IPDEventManager eventManager;

    private Reference patientReference;
    private Concept medicationRequestConcept;

    @Before
    public void setUp() {
        patientReference = new Reference();
        medicationRequestConcept = new Concept();

        PowerMockito.mockStatic(Context.class);
        when(Context.getAdministrationService()).thenReturn(administrationService);
        when(administrationService.getGlobalProperty("bahmni-ipd.allowSlotStopOnDrugOrderStop", "false")).thenReturn("true");
        when(referenceService.getReferenceByTypeAndTargetUUID("org.openmrs.Patient", "patient-uuid"))
                .thenReturn(Optional.of(patientReference));
        when(conceptService.getConceptByName(ServiceType.MEDICATION_REQUEST.conceptName()))
                .thenReturn(medicationRequestConcept);
    }

    private EncounterTransaction.DrugOrder buildStoppedDrugOrder(String previousOrderUuid, Date dateStopped) {
        EncounterTransaction.DrugOrder drugOrder = new EncounterTransaction.DrugOrder();
        drugOrder.setPreviousOrderUuid(previousOrderUuid);
        drugOrder.setDateStopped(dateStopped);
        return drugOrder;
    }

    private Slot buildSlot(String uuid, Slot.SlotStatus status, MedicationAdministration administration, LocalDateTime startDateTime) {
        Slot slot = new Slot();
        slot.setUuid(uuid);
        slot.setStatus(status);
        slot.setMedicationAdministration(administration);
        slot.setStartDateTime(startDateTime);
        return slot;
    }

    private Date toDate(LocalDateTime ldt) {
        return java.sql.Timestamp.valueOf(ldt);
    }

    @Test
    public void shouldMarkAllSlotsStopped_WhenNoSlotsAdministered() {
        LocalDateTime now = LocalDateTime.now();
        Date dateStopped = toDate(now.minusHours(1));

        EncounterTransaction encounterTransaction = new EncounterTransaction();
        encounterTransaction.setPatientUuid("patient-uuid");
        encounterTransaction.setDrugOrders(Collections.singletonList(
                buildStoppedDrugOrder("prev-order-uuid", dateStopped)));

        Slot slot1 = buildSlot("slot-1", Slot.SlotStatus.SCHEDULED, null, now.plusHours(1));
        Slot slot2 = buildSlot("slot-2", Slot.SlotStatus.SCHEDULED, null, now.plusHours(3));

        when(slotService.getSlotsBySubjectReferenceIdAndServiceTypeAndOrderUuids(
                patientReference, medicationRequestConcept, Collections.singletonList("prev-order-uuid")))
                .thenReturn(Arrays.asList(slot1, slot2));

        service.handlePostProcessEncounterTransaction(null, encounterTransaction);

        verify(slotService, times(2)).saveSlot(argThat(slot -> slot.getStatus() == Slot.SlotStatus.STOPPED));
    }

    @Test
    public void shouldMarkNonAdministeredSlotsStopped_WhenSomeSlotsAlreadyAdministered() {
        LocalDateTime now = LocalDateTime.now();
        Date dateStopped = toDate(now.minusHours(2));

        EncounterTransaction encounterTransaction = new EncounterTransaction();
        encounterTransaction.setPatientUuid("patient-uuid");
        encounterTransaction.setDrugOrders(Collections.singletonList(
                buildStoppedDrugOrder("prev-order-uuid", dateStopped)));

        Slot administered = buildSlot("slot-1", Slot.SlotStatus.COMPLETED,
                new MedicationAdministration(), now.minusHours(6));

        Slot notAdministeredFuture = buildSlot("slot-2", Slot.SlotStatus.SCHEDULED,
                null, now.plusHours(1));

        Slot notAdministeredPast = buildSlot("slot-3", Slot.SlotStatus.SCHEDULED,
                null, now.minusHours(4));

        when(slotService.getSlotsBySubjectReferenceIdAndServiceTypeAndOrderUuids(
                patientReference, medicationRequestConcept, Collections.singletonList("prev-order-uuid")))
                .thenReturn(Arrays.asList(administered, notAdministeredFuture, notAdministeredPast));

        service.handlePostProcessEncounterTransaction(null, encounterTransaction);

        // Only the future non-administered slot should be STOPPED
        verify(slotService, times(1)).saveSlot(argThat(slot ->
                slot.getUuid().equals("slot-2") && slot.getStatus() == Slot.SlotStatus.STOPPED));

        verify(slotService, never()).saveSlot(argThat(slot -> slot.getUuid().equals("slot-1")));
        verify(slotService, never()).saveSlot(argThat(slot -> slot.getUuid().equals("slot-3")));
    }

    @Test
    public void shouldNotStopSlots_WhenGlobalPropertyIsFalse() {
        when(administrationService.getGlobalProperty("bahmni-ipd.allowSlotStopOnDrugOrderStop", "false")).thenReturn("false");

        EncounterTransaction encounterTransaction = new EncounterTransaction();
        encounterTransaction.setPatientUuid("patient-uuid");
        encounterTransaction.setDrugOrders(Collections.singletonList(
                buildStoppedDrugOrder("prev-order-uuid", new Date())));

        service.handlePostProcessEncounterTransaction(null, encounterTransaction);

        verify(referenceService, never()).getReferenceByTypeAndTargetUUID(anyString(), anyString());
        verify(slotService, never()).saveSlot(any());
    }
}
