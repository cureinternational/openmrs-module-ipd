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

import java.util.Arrays;
import java.util.Collections;
import java.util.Optional;

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

    private Patient patient;
    private Visit visit;
    private Schedule schedule;
    private DrugOrder order;
    private Concept prnPlaceholderConcept;
    private Reference patientReference;

    @Before
    public void setUp() {
        patient = new Patient();
        visit = new Visit();
        schedule = new Schedule();
        schedule.setId(1);
        order = new DrugOrder();
        order.setUuid("order-uuid");
        patientReference = new Reference();
        prnPlaceholderConcept = new Concept();

        when(patientService.getPatientByUuid("patient-uuid")).thenReturn(patient);
        when(visitService.getActiveVisitsByPatient(patient)).thenReturn(Arrays.asList(visit));
        when(scheduleService.getScheduleByVisit(visit)).thenReturn(schedule);
        when(orderService.getOrderByUuid("order-uuid")).thenReturn(order);
        when(conceptService.getConceptByName(ServiceType.AS_NEEDED_PLACEHOLDER.conceptName()))
                .thenReturn(prnPlaceholderConcept);
        when(referenceService.getReferenceByTypeAndTargetUUID(Patient.class.getTypeName(), "patient-uuid"))
                .thenReturn(Optional.of(patientReference));
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
}