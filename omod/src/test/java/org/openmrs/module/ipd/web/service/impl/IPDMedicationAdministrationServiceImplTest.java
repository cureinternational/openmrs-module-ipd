package org.openmrs.module.ipd.web.service.impl;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;
import org.openmrs.Concept;
import org.openmrs.ConceptName;
import org.openmrs.Encounter;
import org.openmrs.Provider;
import org.openmrs.api.APIException;
import org.openmrs.api.ConceptService;
import org.openmrs.api.ProviderService;
import org.openmrs.api.context.Context;
import org.openmrs.module.fhir2.apiext.FhirMedicationAdministrationService;
import org.openmrs.module.fhir2.apiext.dao.FhirMedicationAdministrationDao;
import org.openmrs.module.fhir2.apiext.translators.MedicationAdministrationTranslator;
import org.openmrs.module.fhir2.model.FhirReference;
import org.openmrs.module.fhir2.model.FhirTask;
import org.openmrs.module.fhirExtension.model.Task;
import org.openmrs.module.fhirExtension.model.TaskSearchRequest;
import org.openmrs.module.fhirExtension.service.TaskService;
import org.openmrs.module.ipd.api.model.MedicationAdministration;
import org.openmrs.module.ipd.api.model.MedicationAdministrationNote;
import org.openmrs.module.ipd.api.model.ServiceType;
import org.openmrs.module.ipd.api.model.Slot;
import org.openmrs.module.ipd.api.service.ScheduleService;
import org.openmrs.module.ipd.api.service.SlotService;
import org.openmrs.module.ipd.api.translators.MedicationAdministrationToSlotStatusTranslator;
import org.openmrs.module.ipd.web.contract.MedicationAdministrationAcknowledgementRequest;
import org.openmrs.module.ipd.web.contract.MedicationAdministrationNoteRequest;
import org.openmrs.module.ipd.web.contract.MedicationAdministrationRequest;
import org.openmrs.module.ipd.web.factory.MedicationAdministrationFactory;
import org.openmrs.module.ipd.web.factory.ScheduleFactory;
import org.openmrs.module.ipd.web.factory.SlotFactory;
import org.openmrs.module.ipd.web.mapper.AcknowledgementTaskMapper;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@RunWith(PowerMockRunner.class)
@PrepareForTest({Context.class})
public class IPDMedicationAdministrationServiceImplTest {

    @Mock
    private FhirMedicationAdministrationService fhirMedicationAdministrationService;
    @Mock
    private MedicationAdministrationTranslator medicationAdministrationTranslator;
    @Mock
    private MedicationAdministrationFactory medicationAdministrationFactory;
    @Mock
    private SlotFactory slotFactory;
    @Mock
    private SlotService slotService;
    @Mock
    private ScheduleService scheduleService;
    @Mock
    private FhirMedicationAdministrationDao fhirMedicationAdministrationDao;
    @Mock
    private MedicationAdministrationToSlotStatusTranslator medicationAdministrationToSlotStatusTranslator;
    @Mock
    private ScheduleFactory scheduleFactory;
    @Mock
    private TaskService taskService;
    @Mock
    private AcknowledgementTaskMapper acknowledgementTaskMapper;

    @InjectMocks
    private IPDMedicationAdministrationServiceImpl service;

    private String medicationAdminUuid;
    private String providerUuid;
    private String encounterUuid;
    private MedicationAdministration medicationAdministration;
    private Provider provider;
    private Encounter encounter;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);

        medicationAdminUuid = "med-admin-uuid-123";
        providerUuid = "provider-uuid-456";
        encounterUuid = "encounter-uuid-789";

        provider = new Provider();
        provider.setUuid(providerUuid);

        encounter = new Encounter();
        encounter.setUuid(encounterUuid);

        medicationAdministration = new MedicationAdministration();
        medicationAdministration.setUuid(medicationAdminUuid);
        medicationAdministration.setEncounter(encounter);
        medicationAdministration.setNotes(new HashSet<>());
    }

    @Test
    public void shouldAddAmendmentNoteSuccessfully() {
        // Arrange
        MedicationAdministrationNoteRequest noteRequest = MedicationAdministrationNoteRequest.builder()
                .authorUuid(providerUuid)
                .text("Dosage corrected from 500mg to 250mg")
                .reason("Initial dosage was incorrect per physician order")
                .recordedTime(System.currentTimeMillis() / 1000)
                .build();

        MedicationAdministrationNote previousNote = new MedicationAdministrationNote();
        previousNote.setUuid("previous-note-uuid");
        previousNote.setText("Original note");
        previousNote.setVoided(false);
        medicationAdministration.getNotes().add(previousNote);

        PowerMockito.mockStatic(Context.class);
        ProviderService providerService = mock(ProviderService.class);
        when(Context.getProviderService()).thenReturn(providerService);
        when(providerService.getProviderByUuid(providerUuid)).thenReturn(provider);

        when(fhirMedicationAdministrationDao.get(medicationAdminUuid))
                .thenReturn(medicationAdministration);
        when(taskService.searchTasks(any(TaskSearchRequest.class)))
                .thenReturn(Collections.emptyList());

        // Act
        MedicationAdministrationNote result = service.amendNote(medicationAdminUuid, noteRequest);

        // Assert
        assertNotNull("Amendment note should be created", result);
        assertEquals("Note text should match request", noteRequest.getText(), result.getText());
        assertEquals("Amendment reason should match request", noteRequest.getReason(), result.getAmendmentReason());
        assertEquals("Author should be the provider", provider, result.getAuthor());
        assertNotNull("Note UUID should be generated", result.getUuid());
        assertNotNull("Previous note should be linked", result.getPreviousNote());
        assertEquals("Previous note UUID should match", previousNote.getUuid(), result.getPreviousNote().getUuid());
        verify(fhirMedicationAdministrationDao, times(1)).createOrUpdate(medicationAdministration);
    }

    @Test(expected = APIException.class)
    public void shouldThrowException_WhenMedicationAdminNotFound_OnAmend() {
        // Arrange
        MedicationAdministrationNoteRequest noteRequest = MedicationAdministrationNoteRequest.builder()
                .authorUuid(providerUuid)
                .text("Test note")
                .reason("Test reason")
                .build();

        when(fhirMedicationAdministrationDao.get(anyString()))
                .thenReturn(null);

        // Act & Assert (exception expected)
        service.amendNote(medicationAdminUuid, noteRequest);
    }

    @Test(expected = APIException.class)
    public void shouldThrowException_WhenMedicationAdminIsLocked_OnAmend() {
        // Arrange
        MedicationAdministrationNoteRequest noteRequest = MedicationAdministrationNoteRequest.builder()
                .authorUuid(providerUuid)
                .text("Attempted amendment")
                .reason("This should fail")
                .build();

        MedicationAdministrationNote existingNote = new MedicationAdministrationNote();
        existingNote.setUuid("note-uuid-001");
        existingNote.setVoided(false);
        medicationAdministration.getNotes().add(existingNote);

        Task acknowledgedTask = new Task();
        FhirTask fhirTask = new FhirTask();
        fhirTask.setUuid("task-uuid-001");
        fhirTask.setStatus(FhirTask.TaskStatus.COMPLETED);
        fhirTask.setName("ACKNOWLEDGE_MEDICATION_NOTE");
        FhirReference forReference = new FhirReference();
        forReference.setTargetUuid("note-uuid-001");
        fhirTask.setForReference(forReference);
        acknowledgedTask.setFhirTask(fhirTask);

        when(fhirMedicationAdministrationDao.get(medicationAdminUuid))
                .thenReturn(medicationAdministration);
        when(taskService.searchTasks(any(TaskSearchRequest.class)))
                .thenReturn(Arrays.asList(acknowledgedTask));

        // Act & Assert (exception expected)
        service.amendNote(medicationAdminUuid, noteRequest);
    }

    @Test
    public void shouldAcknowledgeSuccessfully() {
        // Arrange
        MedicationAdministrationNote noteToAcknowledge = new MedicationAdministrationNote();
        noteToAcknowledge.setUuid("note-uuid-for-ack");
        noteToAcknowledge.setText("Amendment to acknowledge");
        noteToAcknowledge.setVoided(false);
        noteToAcknowledge.setDateCreated(new Date());
        medicationAdministration.getNotes().add(noteToAcknowledge);

        MedicationAdministrationAcknowledgementRequest ackRequest = MedicationAdministrationAcknowledgementRequest.builder()
                .approvedByUuid(providerUuid)
                .remarks("Acknowledged and approved")
                .build();

        Task taskToReturn = new Task();
        FhirTask fhirTask = new FhirTask();
        fhirTask.setUuid("task-uuid-ack");
        fhirTask.setStatus(FhirTask.TaskStatus.COMPLETED);
        taskToReturn.setFhirTask(fhirTask);

        PowerMockito.mockStatic(Context.class);
        ProviderService providerService = mock(ProviderService.class);
        when(Context.getProviderService()).thenReturn(providerService);
        when(providerService.getProviderByUuid(providerUuid)).thenReturn(provider);

        when(fhirMedicationAdministrationDao.get(medicationAdminUuid))
                .thenReturn(medicationAdministration);
        when(taskService.searchTasks(any(TaskSearchRequest.class)))
                .thenReturn(Collections.emptyList());
        when(acknowledgementTaskMapper.createAcknowledgementTask(
                "note-uuid-for-ack",
                encounterUuid,
                "ACKNOWLEDGE_MEDICATION_NOTE",
                "Acknowledged and approved",
                providerUuid))
                .thenReturn(taskToReturn);

        // Act
        Task result = service.acknowledge(medicationAdminUuid, ackRequest);

        // Assert
        assertNotNull("Acknowledgement task should be created", result);
        assertEquals("Task should have COMPLETED status", FhirTask.TaskStatus.COMPLETED, result.getFhirTask().getStatus());
        verify(acknowledgementTaskMapper, times(1))
                .createAcknowledgementTask(
                        "note-uuid-for-ack",
                        encounterUuid,
                        "ACKNOWLEDGE_MEDICATION_NOTE",
                        "Acknowledged and approved",
                        providerUuid);
        verify(taskService, times(1)).saveTask(taskToReturn);
    }

    @Test(expected = APIException.class)
    public void shouldThrowException_WhenMedicationAdminNotFound_OnAcknowledge() {
        // Arrange
        MedicationAdministrationAcknowledgementRequest ackRequest = MedicationAdministrationAcknowledgementRequest.builder()
                .approvedByUuid(providerUuid)
                .remarks("Test")
                .build();

        when(fhirMedicationAdministrationDao.get(anyString()))
                .thenReturn(null);

        // Act & Assert (exception expected)
        service.acknowledge(medicationAdminUuid, ackRequest);
    }

    @Test(expected = APIException.class)
    public void shouldThrowException_WhenMedicationAdminIsAlreadyLocked_OnAcknowledge() {
        // Arrange
        MedicationAdministrationNote noteToAcknowledge = new MedicationAdministrationNote();
        noteToAcknowledge.setUuid("note-uuid-for-ack");
        noteToAcknowledge.setVoided(false);
        noteToAcknowledge.setDateCreated(new Date());
        medicationAdministration.getNotes().add(noteToAcknowledge);

        Task existingAcknowledgementTask = new Task();
        FhirTask fhirTask = new FhirTask();
        fhirTask.setUuid("existing-task-uuid");
        fhirTask.setStatus(FhirTask.TaskStatus.COMPLETED);
        fhirTask.setName("ACKNOWLEDGE_MEDICATION_NOTE");
        FhirReference forReference = new FhirReference();
        forReference.setTargetUuid("note-uuid-for-ack");
        fhirTask.setForReference(forReference);
        existingAcknowledgementTask.setFhirTask(fhirTask);

        MedicationAdministrationAcknowledgementRequest ackRequest = MedicationAdministrationAcknowledgementRequest.builder()
                .approvedByUuid(providerUuid)
                .remarks("Should fail")
                .build();

        when(fhirMedicationAdministrationDao.get(medicationAdminUuid))
                .thenReturn(medicationAdministration);
        when(taskService.searchTasks(any(TaskSearchRequest.class)))
                .thenReturn(Arrays.asList(existingAcknowledgementTask));

        // Act & Assert (exception expected)
        service.acknowledge(medicationAdminUuid, ackRequest);
    }

    @Test(expected = APIException.class)
    public void shouldThrowException_WhenNoNotesExist_OnAcknowledge() {
        // Arrange
        medicationAdministration.setNotes(new HashSet<>());

        MedicationAdministrationAcknowledgementRequest ackRequest = MedicationAdministrationAcknowledgementRequest.builder()
                .approvedByUuid(providerUuid)
                .remarks("No notes to acknowledge")
                .build();

        when(fhirMedicationAdministrationDao.get(medicationAdminUuid))
                .thenReturn(medicationAdministration);
        when(taskService.searchTasks(any(TaskSearchRequest.class)))
                .thenReturn(Collections.emptyList());

        // Act & Assert (exception expected)
        service.acknowledge(medicationAdminUuid, ackRequest);
    }

    @Test
    public void shouldConvertPrnPlaceholderToAsNeededMedicationRequest_WhenSavingScheduledAdministration() {
        String slotUuid = "prn-slot-uuid";
        long administeredDateTime = System.currentTimeMillis() / 1000;

        Concept prnConcept = mock(Concept.class);
        ConceptName prnConceptName = mock(ConceptName.class);
        when(prnConcept.getName()).thenReturn(prnConceptName);
        when(prnConceptName.getName()).thenReturn(ServiceType.AS_NEEDED_PLACEHOLDER.conceptName());

        Slot slot = new Slot();
        slot.setServiceType(prnConcept);
        slot.setStatus(Slot.SlotStatus.SCHEDULED);
        slot.setStartDateTime(LocalDateTime.now().minusHours(2));

        Concept adminConcept = new Concept();

        org.hl7.fhir.r4.model.MedicationAdministration fhirAdmin =
                mock(org.hl7.fhir.r4.model.MedicationAdministration.class);
        when(fhirAdmin.getId()).thenReturn("admin-id");

        MedicationAdministration openmrsAdmin = new MedicationAdministration();

        MedicationAdministrationRequest request = MedicationAdministrationRequest.builder()
                .slotUuid(slotUuid)
                .administeredDateTime(administeredDateTime)
                .build();

        when(slotService.getSlotByUUID(slotUuid)).thenReturn(slot);
        when(medicationAdministrationFactory.mapRequestToMedicationAdministration(any(), any()))
                .thenReturn(new MedicationAdministration());
        when(medicationAdministrationTranslator.toFhirResource(any())).thenReturn(fhirAdmin);
        when(fhirMedicationAdministrationService.create(any())).thenReturn(fhirAdmin);
        when(fhirMedicationAdministrationDao.get("admin-id")).thenReturn(openmrsAdmin);
        when(medicationAdministrationToSlotStatusTranslator.toSlotStatus(any()))
                .thenReturn(Slot.SlotStatus.COMPLETED);

        PowerMockito.mockStatic(Context.class);
        ConceptService conceptService = mock(ConceptService.class);
        when(Context.getConceptService()).thenReturn(conceptService);
        when(conceptService.getConceptByName(ServiceType.AS_NEEDED_MEDICATION_REQUEST.conceptName()))
                .thenReturn(adminConcept);

        service.saveScheduledMedicationAdministration(request);

        assertEquals("Service type should be converted to AsNeededMedicationRequest", adminConcept, slot.getServiceType());
        verify(slotService).saveSlot(slot);
    }

    @Test
    public void shouldNotConvertServiceType_WhenSlotIsNotPrnPlaceholder() {
        String slotUuid = "regular-slot-uuid";

        Concept regularConcept = mock(Concept.class);
        ConceptName regularConceptName = mock(ConceptName.class);
        when(regularConcept.getName()).thenReturn(regularConceptName);
        when(regularConceptName.getName()).thenReturn(ServiceType.MEDICATION_REQUEST.conceptName());

        Slot slot = new Slot();
        slot.setServiceType(regularConcept);
        slot.setStatus(Slot.SlotStatus.SCHEDULED);
        slot.setStartDateTime(LocalDateTime.now().minusHours(1));

        org.hl7.fhir.r4.model.MedicationAdministration fhirAdmin =
                mock(org.hl7.fhir.r4.model.MedicationAdministration.class);
        when(fhirAdmin.getId()).thenReturn("admin-id");

        MedicationAdministration openmrsAdmin = new MedicationAdministration();

        MedicationAdministrationRequest request = MedicationAdministrationRequest.builder()
                .slotUuid(slotUuid)
                .administeredDateTime(System.currentTimeMillis() / 1000)
                .build();

        when(slotService.getSlotByUUID(slotUuid)).thenReturn(slot);
        when(medicationAdministrationFactory.mapRequestToMedicationAdministration(any(), any()))
                .thenReturn(new MedicationAdministration());
        when(medicationAdministrationTranslator.toFhirResource(any())).thenReturn(fhirAdmin);
        when(fhirMedicationAdministrationService.create(any())).thenReturn(fhirAdmin);
        when(fhirMedicationAdministrationDao.get("admin-id")).thenReturn(openmrsAdmin);
        when(medicationAdministrationToSlotStatusTranslator.toSlotStatus(any()))
                .thenReturn(Slot.SlotStatus.COMPLETED);

        service.saveScheduledMedicationAdministration(request);

        assertEquals("Service type should not be changed for a non-PRN slot", regularConcept, slot.getServiceType());
        verify(slotService).saveSlot(slot);
    }
}
