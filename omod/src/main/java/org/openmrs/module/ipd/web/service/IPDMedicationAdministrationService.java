package org.openmrs.module.ipd.web.service;

import org.hl7.fhir.r4.model.MedicationAdministration;
import org.openmrs.module.fhirExtension.model.Task;
import org.openmrs.module.ipd.api.model.MedicationAdministrationNote;
import org.openmrs.module.ipd.web.contract.MedicationAdministrationAcknowledgementRequest;
import org.openmrs.module.ipd.web.contract.MedicationAdministrationNoteRequest;
import org.openmrs.module.ipd.web.contract.MedicationAdministrationRequest;

public interface IPDMedicationAdministrationService {

    MedicationAdministration saveScheduledMedicationAdministration(MedicationAdministrationRequest medicationAdministrationRequest);

    MedicationAdministration updateAdhocMedicationAdministration(String uuid,MedicationAdministrationRequest medicationAdministrationRequest);

    MedicationAdministration saveAdhocMedicationAdministration(MedicationAdministrationRequest medicationAdministrationRequest);

    MedicationAdministrationNote amendNote(String medicationAdministrationUuid, MedicationAdministrationNoteRequest noteRequest);

    Task acknowledge(String medicationAdministrationUuid, MedicationAdministrationAcknowledgementRequest acknowledgementRequest);
}
