package org.openmrs.module.ipd.web.service.impl;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.openmrs.Concept;
import org.openmrs.DrugOrder;
import org.openmrs.Patient;
import org.openmrs.Visit;
import org.openmrs.api.ConceptService;
import org.openmrs.api.OrderService;
import org.openmrs.api.PatientService;
import org.openmrs.api.VisitService;
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

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.Optional;

import org.openmrs.api.APIException;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@RunWith(MockitoJUnitRunner.class)
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
        when(orderService.getOrderByUuid("order-uuid")).thenReturn(order);
        when(conceptService.getConceptByName(ServiceType.AS_NEEDED_PLACEHOLDER.conceptName()))
                .thenReturn(prnPlaceholderConcept);
        when(conceptService.getConceptByName(ServiceType.MEDICATION_REQUEST.conceptName()))
                .thenReturn(medicationRequestConcept);
        when(referenceService.getReferenceByTypeAndTargetUUID(Patient.class.getTypeName(), "patient-uuid"))
                .thenReturn(Optional.of(patientReference));
        when(slotTimeCreationService.createSlotsStartTimeFrom(any(), any()))
                .thenReturn(Collections.emptyList());
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
}