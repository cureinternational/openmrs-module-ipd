package org.openmrs.module.ipd.web.controller;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.openmrs.api.context.Context;
import org.openmrs.api.context.UserContext;
import org.openmrs.module.ipd.api.model.MedicationAdministrationNote;
import org.openmrs.module.ipd.web.contract.NoteAcknowledgeRequest;
import org.openmrs.module.ipd.web.contract.NoteAmendmentRequest;
import org.openmrs.module.ipd.web.factory.MedicationAdministrationFactory;
import org.openmrs.module.ipd.web.service.IPDMedicationAdministrationService;
import org.openmrs.module.ipd.web.validators.NoteAcknowledgeRequestValidator;
import org.openmrs.module.ipd.web.validators.NoteAmendmentRequestValidator;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Date;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;

@PrepareForTest(Context.class)
@RunWith(PowerMockRunner.class)
public class IPDMedicationAdministrationControllerTest {
    @Mock
    IPDMedicationAdministrationService ipdMedicationAdministrationService;
    @Mock
    private MedicationAdministrationFactory medicationAdministrationFactory;
    @Mock
    private NoteAmendmentRequestValidator noteAmendmentRequestValidator;
    @Mock
    private NoteAcknowledgeRequestValidator noteAcknowledgeRequestValidator;
    @Rule
    public ExpectedException expectedException = ExpectedException.none();
    @Mock
    UserContext userContext;

    @InjectMocks
    private IPDMedicationAdministrationController ipdMedicationAdministrationController;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        PowerMockito.mockStatic(Context.class);
        PowerMockito.when(Context.getUserContext()).thenReturn(userContext);
        when(userContext.hasPrivilege(any())).thenReturn(true);
    }


    @Test
    public void shouldAmendNoteSuccessfully() {
        String noteUuid = "note-uuid-123";
        NoteAmendmentRequest amendmentRequest = new NoteAmendmentRequest("Updated note content", "Correction", "user-uuid-456");

        Mockito.doNothing().when(noteAmendmentRequestValidator).validate(any(), any());
        when(ipdMedicationAdministrationService.amendNote(noteUuid, amendmentRequest))
                .thenReturn(new MedicationAdministrationNote());

        ResponseEntity<Object> result = ipdMedicationAdministrationController.amendNote(noteUuid, amendmentRequest);

        assertEquals(HttpStatus.OK, result.getStatusCode());
//        verify(ipdMedicationAdministrationService, times(1)).amendNote(any(), any());
    }

    @Test
    public void shouldAcknowledgeAmendmentSuccessfully() {
        String noteUuid = "note-uuid-123";
        NoteAcknowledgeRequest acknowledgeRequest = new NoteAcknowledgeRequest("Approved", "user-uuid-456", new Date().getTime(), "Looks good");
        MedicationAdministrationNote expectedNote = new MedicationAdministrationNote();

        doNothing().when(noteAcknowledgeRequestValidator).validate(any(NoteAcknowledgeRequest.class), any());
        when(ipdMedicationAdministrationService.acknowledgeAmendment(noteUuid, acknowledgeRequest))
                .thenReturn(expectedNote);

        ResponseEntity<Object> result = ipdMedicationAdministrationController.acknowledgeAmendment(noteUuid, acknowledgeRequest);

        assertEquals(HttpStatus.OK, result.getStatusCode());
//        verify(ipdMedicationAdministrationService, times(1)).acknowledgeAmendment(noteUuid, acknowledgeRequest);
    }


}
