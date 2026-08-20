package org.openmrs.module.ipd.web.service.impl;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.openmrs.Concept;
import org.openmrs.DrugOrder;
import org.openmrs.Patient;
import org.openmrs.Visit;
import org.openmrs.api.AdministrationService;
import org.openmrs.api.ConceptService;
import org.openmrs.api.OrderService;
import org.openmrs.api.PatientService;
import org.openmrs.api.VisitService;
import org.openmrs.api.context.Context;
import org.openmrs.module.emrapi.encounter.domain.EncounterTransaction;
import org.openmrs.module.ipd.api.events.IPDEventManager;
import org.openmrs.module.ipd.api.model.MedicationAdministration;
import org.openmrs.module.ipd.api.model.Reference;
import org.openmrs.module.ipd.api.model.Schedule;
import org.openmrs.module.ipd.api.model.ServiceType;
import org.openmrs.module.ipd.api.model.Slot;
import org.openmrs.module.ipd.api.service.ReferenceService;
import org.openmrs.module.ipd.api.service.ScheduleService;
import org.openmrs.module.ipd.api.service.SlotService;
import org.openmrs.module.ipd.web.contract.ScheduleMedicationRequest;
import org.openmrs.module.ipd.web.factory.SlotFactory;
import org.openmrs.module.ipd.web.model.SlotTimeCreationResult;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.Optional;

import org.openmrs.api.APIException;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@RunWith(PowerMockRunner.class)
@PrepareForTest({Context.class})
public class IPDScheduleServiceImplTest {

    @InjectMocks
    private IPDScheduleServiceImpl service;

    @Mock private ScheduleService scheduleService;
    @Mock private SlotFactory slotFactory;
    @Mock private SlotService slotService;
    @Mock private ConceptService conceptService;
    @Mock private ReferenceService referenceService;
    @Mock private VisitService visitService;
    @Mock private PatientService patientService;
    @Mock private OrderService orderService;
    @Mock private SlotTimeCreationService slotTimeCreationService;
    @Mock private AdministrationService administrationService;
    @Mock private IPDEventManager eventManager;

    private Patient patient;
    private Visit visit;
    private Schedule schedule;
    private DrugOrder order;
    private Concept prnPlaceholderConcept;
    private Concept medicationRequestConcept;
    private Reference patientReference;

    @Before
    public void setUp() {
        patient = new Patient();
        patient.setUuid("patient-uuid");
        visit = new Visit();
        schedule = new Schedule();
        schedule.setId(1);
        order = new DrugOrder();
        order.setUuid("order-uuid");
        patientReference = new Reference();
        prnPlaceholderConcept = new Concept();
        medicationRequestConcept = new Concept();

        when(patientService.getPatientByUuid("patient-uuid")).thenReturn(patient);
        when(visitService.getActiveVisitsByPatient(patient)).thenReturn(Arrays.asList(visit));
        when(scheduleService.getScheduleByVisit(visit)).thenReturn(schedule);
        when(scheduleService.saveSchedule(any())).thenReturn(schedule);
        when(orderService.getOrderByUuid("order-uuid")).thenReturn(order);
        when(conceptService.getConceptByName(ServiceType.AS_NEEDED_PLACEHOLDER.conceptName()))
                .thenReturn(prnPlaceholderConcept);
        when(conceptService.getConceptByName(ServiceType.MEDICATION_REQUEST.conceptName()))
                .thenReturn(medicationRequestConcept);
        when(referenceService.getReferenceByTypeAndTargetUUID(Patient.class.getTypeName(), "patient-uuid"))
                .thenReturn(Optional.of(patientReference));

        PowerMockito.mockStatic(Context.class);
        when(Context.getAdministrationService()).thenReturn(administrationService);
        when(slotTimeCreationService.createSlotsStartTimeFrom(any(), any()))
                .thenReturn(SlotTimeCreationResult.withoutCrossingTags(Collections.emptyList()));
    }

    @Test
    public void shouldNotCreatePlaceholderSlot_WhenScheduledPlaceholderWithNoAdminAlreadyExists() {
        Slot existingPlaceholder = new Slot();
        existingPlaceholder.setStatus(Slot.SlotStatus.SCHEDULED);
        existingPlaceholder.setMedicationAdministration(null);

        when(slotService.getSlotsBySubjectReferenceIdAndServiceTypeAndOrderUuids(any(), any(), any()))
                .thenReturn(Arrays.asList(existingPlaceholder));

        ScheduleMedicationRequest request = ScheduleMedicationRequest.builder()
                .patientUuid("patient-uuid")
                .orderUuid("order-uuid")
                .serviceType(ServiceType.AS_NEEDED_PLACEHOLDER)
                .build();

        service.saveMedicationSchedule(request);

        verify(slotFactory, never()).createAsNeededPlaceholderSlot(any(), any(), any());
        verify(slotService, never()).saveSlot(any());
    }

    @Test
    public void shouldCreatePlaceholderSlot_WhenNoExistingPlaceholderFound() {
        Slot newPlaceholder = new Slot();

        when(slotService.getSlotsBySubjectReferenceIdAndServiceTypeAndOrderUuids(any(), any(), any()))
                .thenReturn(Collections.emptyList());
        when(slotFactory.createAsNeededPlaceholderSlot(schedule, order, null))
                .thenReturn(newPlaceholder);

        ScheduleMedicationRequest request = ScheduleMedicationRequest.builder()
                .patientUuid("patient-uuid")
                .orderUuid("order-uuid")
                .serviceType(ServiceType.AS_NEEDED_PLACEHOLDER)
                .build();

        service.saveMedicationSchedule(request);

        verify(slotFactory).createAsNeededPlaceholderSlot(schedule, order, null);
        verify(slotService).saveSlot(newPlaceholder);
    }

    @Test
    public void shouldCreateNewPlaceholderSlot_WhenExistingPlaceholderHasBeenAdministered() {
        MedicationAdministration administration = new MedicationAdministration();
        Slot administeredPlaceholder = new Slot();
        administeredPlaceholder.setStatus(Slot.SlotStatus.COMPLETED);
        administeredPlaceholder.setMedicationAdministration(administration);

        Slot newPlaceholder = new Slot();

        when(slotService.getSlotsBySubjectReferenceIdAndServiceTypeAndOrderUuids(any(), any(), any()))
                .thenReturn(Arrays.asList(administeredPlaceholder));
        when(slotFactory.createAsNeededPlaceholderSlot(schedule, order, null))
                .thenReturn(newPlaceholder);

        ScheduleMedicationRequest request = ScheduleMedicationRequest.builder()
                .patientUuid("patient-uuid")
                .orderUuid("order-uuid")
                .serviceType(ServiceType.AS_NEEDED_PLACEHOLDER)
                .build();

        service.saveMedicationSchedule(request);

        verify(slotFactory).createAsNeededPlaceholderSlot(schedule, order, null);
        verify(slotService).saveSlot(newPlaceholder);
    }

    @Test
    public void shouldNotCreatePlaceholderSlot_WhenScheduledPlaceholderExistsAlongsideAdministeredOnes() {
        MedicationAdministration administration = new MedicationAdministration();
        Slot administeredPlaceholder = new Slot();
        administeredPlaceholder.setStatus(Slot.SlotStatus.COMPLETED);
        administeredPlaceholder.setMedicationAdministration(administration);

        Slot scheduledPlaceholder = new Slot();
        scheduledPlaceholder.setStatus(Slot.SlotStatus.SCHEDULED);
        scheduledPlaceholder.setMedicationAdministration(null);

        when(slotService.getSlotsBySubjectReferenceIdAndServiceTypeAndOrderUuids(any(), any(), any()))
                .thenReturn(Arrays.asList(administeredPlaceholder, scheduledPlaceholder));

        ScheduleMedicationRequest request = ScheduleMedicationRequest.builder()
                .patientUuid("patient-uuid")
                .orderUuid("order-uuid")
                .serviceType(ServiceType.AS_NEEDED_PLACEHOLDER)
                .build();

        service.saveMedicationSchedule(request);

        verify(slotFactory, never()).createAsNeededPlaceholderSlot(any(), any(), any());
        verify(slotService, never()).saveSlot(any());
    }

    // ---------------------------------------------------------------------------
    // Per-stage duplicate check tests
    // ---------------------------------------------------------------------------

    @Test
    public void shouldAllowSavingNewStage_WhenDifferentStageAlreadyHasSlots() {
        Slot existingStage1Slot = new Slot();
        existingStage1Slot.setVariableDosageSequence(1);
        existingStage1Slot.setStatus(Slot.SlotStatus.SCHEDULED);

        when(slotService.getSlotsBySubjectReferenceIdAndServiceTypeAndOrderUuids(any(), any(), any()))
                .thenReturn(Arrays.asList(existingStage1Slot));
        when(slotFactory.createSlotsForMedicationFrom(any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(Collections.emptyList());

        ScheduleMedicationRequest request = ScheduleMedicationRequest.builder()
                .patientUuid("patient-uuid")
                .orderUuid("order-uuid")
                .serviceType(ServiceType.MEDICATION_REQUEST)
                .variableDosageSequence(2)
                .build();

        service.saveMedicationSchedule(request);

        verify(slotFactory).createSlotsForMedicationFrom(any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    public void shouldThrowException_WhenSameStageAlreadyHasSlots() {
        Slot existingStage2Slot = new Slot();
        existingStage2Slot.setVariableDosageSequence(2);
        existingStage2Slot.setStatus(Slot.SlotStatus.SCHEDULED);

        when(slotService.getSlotsBySubjectReferenceIdAndServiceTypeAndOrderUuids(any(), any(), any()))
                .thenReturn(Arrays.asList(existingStage2Slot));

        ScheduleMedicationRequest request = ScheduleMedicationRequest.builder()
                .patientUuid("patient-uuid")
                .orderUuid("order-uuid")
                .serviceType(ServiceType.MEDICATION_REQUEST)
                .variableDosageSequence(2)
                .build();

        try {
            service.saveMedicationSchedule(request);
            fail("Expected APIException to be thrown");
        } catch (APIException e) {
            assertNotNull(e.getMessage());
        }
    }

    @Test
    public void shouldThrowException_WhenSlotsAlreadyExistForRegularOrder() {
        Slot existingSlot = new Slot();
        existingSlot.setVariableDosageSequence(null);
        existingSlot.setStatus(Slot.SlotStatus.SCHEDULED);

        when(slotService.getSlotsBySubjectReferenceIdAndServiceTypeAndOrderUuids(any(), any(), any()))
                .thenReturn(Arrays.asList(existingSlot));

        ScheduleMedicationRequest request = ScheduleMedicationRequest.builder()
                .patientUuid("patient-uuid")
                .orderUuid("order-uuid")
                .serviceType(ServiceType.MEDICATION_REQUEST)
                .variableDosageSequence(null)
                .build();

        try {
            service.saveMedicationSchedule(request);
            fail("Expected APIException to be thrown");
        } catch (APIException e) {
            assertNotNull(e.getMessage());
        }
    }

    // ---------------------------------------------------------------------------
    // updateMedicationSchedule void-by-stage tests
    // ---------------------------------------------------------------------------

    @Test
    public void shouldVoidOnlyTargetStageSlots_WhenUpdatingMedicationSchedule() {
        Slot stage1Slot = new Slot();
        stage1Slot.setVariableDosageSequence(1);
        stage1Slot.setStatus(Slot.SlotStatus.SCHEDULED);
        stage1Slot.setStartDateTime(LocalDateTime.now().plusHours(1));

        Slot stage2Slot = new Slot();
        stage2Slot.setVariableDosageSequence(2);
        stage2Slot.setStatus(Slot.SlotStatus.SCHEDULED);
        stage2Slot.setStartDateTime(LocalDateTime.now().plusHours(2));

        // First call (from voidExistingMedicationSlotsForOrder) returns both slots;
        // second call (from saveMedicationSchedule duplicate check) returns empty — stage already voided.
        when(slotService.getSlotsBySubjectReferenceIdAndServiceTypeAndOrderUuids(any(), any(), any()))
                .thenReturn(Arrays.asList(stage1Slot, stage2Slot))
                .thenReturn(Collections.emptyList());
        when(slotFactory.createSlotsForMedicationFrom(any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(Collections.emptyList());

        ScheduleMedicationRequest request = ScheduleMedicationRequest.builder()
                .patientUuid("patient-uuid")
                .orderUuid("order-uuid")
                .serviceType(ServiceType.MEDICATION_REQUEST)
                .variableDosageSequence(1)
                .build();

        service.updateMedicationSchedule(request);

        // Only stage-1 slot should have been voided
        verify(slotService, times(1)).voidSlot(any(), any());
        verify(slotService).voidSlot(stage1Slot, "Edit drug chart");
    }

    @Test
    public void shouldVoidAllSlots_WhenUpdatingRegularOrder() {
        Slot slot1 = new Slot();
        slot1.setVariableDosageSequence(null);
        slot1.setStatus(Slot.SlotStatus.SCHEDULED);
        slot1.setStartDateTime(LocalDateTime.now().plusHours(1));

        Slot slot2 = new Slot();
        slot2.setVariableDosageSequence(null);
        slot2.setStatus(Slot.SlotStatus.SCHEDULED);
        slot2.setStartDateTime(LocalDateTime.now().plusHours(2));

        // First call (void phase) returns both slots; second call (duplicate check after void) returns empty.
        when(slotService.getSlotsBySubjectReferenceIdAndServiceTypeAndOrderUuids(any(), any(), any()))
                .thenReturn(Arrays.asList(slot1, slot2))
                .thenReturn(Collections.emptyList());
        when(slotFactory.createSlotsForMedicationFrom(any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(Collections.emptyList());

        ScheduleMedicationRequest request = ScheduleMedicationRequest.builder()
                .patientUuid("patient-uuid")
                .orderUuid("order-uuid")
                .serviceType(ServiceType.MEDICATION_REQUEST)
                .variableDosageSequence(null)
                .build();

        service.updateMedicationSchedule(request);

        verify(slotService, times(2)).voidSlot(any(), any());
    }

    @Test
    public void shouldReturnMedicationSlots_ByPatientAndServiceType() {
        Slot slot = new Slot();
        slot.setUuid("slot-uuid");

        when(slotService.getSlotsBySubjectReferenceIdAndServiceType(patientReference, medicationRequestConcept))
                .thenReturn(Arrays.asList(slot));

        java.util.List<Slot> result = service.getMedicationSlots("patient-uuid", ServiceType.MEDICATION_REQUEST);

        assertNotNull("Slots should not be null", result);
        assert(result.size() == 1);
        verify(slotService).getSlotsBySubjectReferenceIdAndServiceType(patientReference, medicationRequestConcept);
    }

    @Test
    public void shouldReturnMedicationSlots_ByPatientServiceTypeAndDate() {
        Slot slot = new Slot();
        slot.setUuid("slot-uuid");
        java.time.LocalDate date = java.time.LocalDate.now();

        when(slotService.getSlotsBySubjectReferenceIdAndForDateAndServiceType(patientReference, date, medicationRequestConcept))
                .thenReturn(Arrays.asList(slot));

        java.util.List<Slot> result = service.getMedicationSlots("patient-uuid", ServiceType.MEDICATION_REQUEST, date);

        assertNotNull("Slots should not be null", result);
        assert(result.size() == 1);
        verify(slotService).getSlotsBySubjectReferenceIdAndForDateAndServiceType(patientReference, date, medicationRequestConcept);
    }

    @Test
    public void shouldReturnMedicationSlots_ByPatientServiceTypeAndOrderUuids() {
        Slot slot = new Slot();
        slot.setUuid("slot-uuid");
        java.util.List<String> orderUuids = Arrays.asList("order-uuid");

        when(slotService.getSlotsBySubjectReferenceIdAndServiceTypeAndOrderUuids(patientReference, medicationRequestConcept, orderUuids))
                .thenReturn(Arrays.asList(slot));

        java.util.List<Slot> result = service.getMedicationSlots("patient-uuid", ServiceType.MEDICATION_REQUEST, orderUuids);

        assertNotNull("Slots should not be null", result);
        assert(result.size() == 1);
        verify(slotService).getSlotsBySubjectReferenceIdAndServiceTypeAndOrderUuids(patientReference, medicationRequestConcept, orderUuids);
    }

    @Test
    public void shouldReturnEmptySlots_WhenReferenceNotFound() {
        when(referenceService.getReferenceByTypeAndTargetUUID(Patient.class.getTypeName(), "patient-uuid"))
                .thenReturn(Optional.empty());

        java.util.List<Slot> result = service.getMedicationSlots("patient-uuid", ServiceType.MEDICATION_REQUEST);

        assertNotNull("Should return empty list, not null", result);
        assert(result.isEmpty());
    }

    @Test
    public void shouldReturnMedicationSlots_ForGivenTimeFrame() {
        Slot slot = new Slot();
        slot.setUuid("slot-uuid");
        LocalDateTime startDate = LocalDateTime.now();
        LocalDateTime endDate = LocalDateTime.now().plusDays(7);

        when(slotService.getSlotsBySubjectReferenceIdAndForTheGivenTimeFrame(patientReference, startDate, endDate, visit))
                .thenReturn(Arrays.asList(slot));

        java.util.List<Slot> result = service.getMedicationSlotsForTheGivenTimeFrame("patient-uuid", startDate, endDate, false, visit);

        assertNotNull("Slots should not be null", result);
        assert(result.size() == 1);
        verify(slotService).getSlotsBySubjectReferenceIdAndForTheGivenTimeFrame(patientReference, startDate, endDate, visit);
    }

    @Test
    public void shouldReturnMedicationSlots_ConsideringAdministeredTime() {
        Slot slot = new Slot();
        slot.setUuid("slot-uuid");
        LocalDateTime startDate = LocalDateTime.now();
        LocalDateTime endDate = LocalDateTime.now().plusDays(7);

        when(slotService.getSlotsBySubjectReferenceIncludingAdministeredTimeFrame(patientReference, startDate, endDate, visit))
                .thenReturn(Arrays.asList(slot));

        java.util.List<Slot> result = service.getMedicationSlotsForTheGivenTimeFrame("patient-uuid", startDate, endDate, true, visit);

        assertNotNull("Slots should not be null", result);
        assert(result.size() == 1);
        verify(slotService).getSlotsBySubjectReferenceIncludingAdministeredTimeFrame(patientReference, startDate, endDate, visit);
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
        when(administrationService.getGlobalProperty("bahmni-ipd.allowSlotStopOnDrugOrderStop", "false")).thenReturn("true");

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
        when(administrationService.getGlobalProperty("bahmni-ipd.allowSlotStopOnDrugOrderStop", "false")).thenReturn("true");

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