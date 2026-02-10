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
import org.openmrs.Encounter;
import org.openmrs.Provider;
import org.openmrs.api.APIException;
import org.openmrs.api.context.Context;
import org.openmrs.api.ProviderService;
import org.openmrs.module.fhir2.apiext.dao.FhirMedicationAdministrationDao;
import org.openmrs.module.fhir2.model.FhirReference;
import org.openmrs.module.fhir2.model.FhirTask;
import org.openmrs.module.fhirExtension.model.Task;
import org.openmrs.module.fhirExtension.model.TaskSearchRequest;
import org.openmrs.module.fhirExtension.service.TaskService;
import org.openmrs.module.ipd.api.model.MedicationAdministration;
import org.openmrs.module.ipd.api.model.MedicationAdministrationNote;
import org.openmrs.module.ipd.web.contract.MedicationAdministrationAcknowledgementRequest;
import org.openmrs.module.ipd.web.contract.MedicationAdministrationNoteRequest;
import org.openmrs.module.ipd.web.mapper.AcknowledgementTaskMapper;

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
    private FhirMedicationAdministrationDao fhirMedicationAdministrationDao;

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
}
