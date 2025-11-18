package org.openmrs.module.ipd.web.service;

import org.hl7.fhir.r4.model.MedicationAdministration;
import org.openmrs.module.ipd.api.model.MedicationAdministrationNote;
import org.openmrs.module.ipd.web.contract.MedicationAdministrationRequest;
import org.openmrs.module.ipd.web.contract.NoteAmendmentRequest;
import org.openmrs.module.ipd.web.contract.NoteAcknowledgeRequest;

public interface IPDMedicationAdministrationService {

    MedicationAdministration saveScheduledMedicationAdministration(MedicationAdministrationRequest medicationAdministrationRequest);

    MedicationAdministration updateAdhocMedicationAdministration(String uuid,MedicationAdministrationRequest medicationAdministrationRequest);

    MedicationAdministration saveAdhocMedicationAdministration(MedicationAdministrationRequest medicationAdministrationRequest);

    /**
     * Amend a medication administration note
     * @param noteUuid UUID of the note to amend
     * @param amendmentRequest Amendment details
     * @return The newly created amendment note
     */
    MedicationAdministrationNote amendNote(String noteUuid, NoteAmendmentRequest amendmentRequest);

    /**
     * Acknowledge (approve or reject) a note amendment
     * @param noteUuid UUID of the amendment note to acknowledge
     * @param acknowledgeRequest Acknowledgement details
     * @return The updated amendment note with approval status
     */
    MedicationAdministrationNote acknowledgeAmendment(String noteUuid, NoteAcknowledgeRequest acknowledgeRequest);

}
