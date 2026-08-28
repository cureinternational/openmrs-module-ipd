package org.openmrs.module.ipd.web.contract;

import org.junit.Test;
import org.openmrs.Concept;
import org.openmrs.module.ipd.api.model.MedicationAdministrationNote;

import java.util.Date;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

public class MedicationAdministrationNoteResponseTest {

    @Test
    public void shouldNotThrowNPE_WhenStatusReasonConceptHasNoName() {
        // Arrange
        Concept statusReasonConcept = new Concept();
        statusReasonConcept.setConceptId(999);
        // Intentionally no ConceptName added, so getName() returns null.

        MedicationAdministrationNote note = new MedicationAdministrationNote();
        note.setUuid("note-uuid-no-concept-name");
        note.setText("Amendment with unnamed reason concept");
        note.setRecordedTime(new Date());
        note.setStatusReason(statusReasonConcept);

        // Act
        MedicationAdministrationNoteResponse response = MedicationAdministrationNoteResponse.createFrom(note);

        // Assert
        assertNotNull("Response should still be created", response);
        assertNull("Amendment reason should be null when the concept has no name in any locale", response.getAmendmentReason());
    }
}
