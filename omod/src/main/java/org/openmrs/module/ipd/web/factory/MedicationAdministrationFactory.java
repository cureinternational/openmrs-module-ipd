package org.openmrs.module.ipd.web.factory;

import org.openmrs.DrugOrder;
import org.openmrs.api.context.Context;
import org.openmrs.module.fhir2.apiext.translators.MedicationAdministrationTranslator;
import org.openmrs.module.ipd.api.model.MedicationAdministration;
import org.openmrs.module.ipd.api.model.MedicationAdministrationNote;
import org.openmrs.module.ipd.api.model.MedicationAdministrationPerformer;
import org.openmrs.module.ipd.web.contract.MedicationAdministrationNoteRequest;
import org.openmrs.module.ipd.web.contract.MedicationAdministrationPerformerRequest;
import org.openmrs.module.ipd.web.contract.MedicationAdministrationRequest;
import org.openmrs.module.ipd.web.contract.MedicationAdministrationResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;


@Component
public class MedicationAdministrationFactory {

    private MedicationAdministrationTranslator medicationAdministrationTranslator;

    @Autowired
    public MedicationAdministrationFactory(MedicationAdministrationTranslator medicationAdministrationTranslator) {
        this.medicationAdministrationTranslator = medicationAdministrationTranslator;
    }

    public MedicationAdministration mapRequestToMedicationAdministration(MedicationAdministrationRequest request, MedicationAdministration existingMedicationAdministration) {

        MedicationAdministration medicationAdministration = new MedicationAdministration();
        if (existingMedicationAdministration ==null ||  existingMedicationAdministration.getId() == null) {
            medicationAdministration.setAdministeredDateTime(request.getAdministeredDateTimeAsLocaltime());
            medicationAdministration.setStatus(org.hl7.fhir.r4.model.MedicationAdministration.MedicationAdministrationStatus.fromCode(request.getStatus()));
            medicationAdministration.setPatient(Context.getPatientService().getPatientByUuid(request.getPatientUuid()));
            medicationAdministration.setEncounter(Context.getEncounterService().getEncounterByUuid(request.getEncounterUuid()));
            medicationAdministration.setDrugOrder((DrugOrder) Context.getOrderService().getOrderByUuid(request.getOrderUuid()));
            medicationAdministration.setDrug(Context.getConceptService().getDrugByUuid(request.getDrugUuid()));
            medicationAdministration.setDosingInstructions(request.getDosingInstructions());
            medicationAdministration.setDose(request.getDose());
            medicationAdministration.setDoseUnits(Context.getConceptService().getConceptByName(request.getDoseUnits()));
            medicationAdministration.setRoute(Context.getConceptService().getConceptByName(request.getRoute()));
            medicationAdministration.setSite(Context.getConceptService().getConceptByName(request.getSite()));
        }
        else {
            medicationAdministration.setUuid(existingMedicationAdministration.getUuid());
        }
        List<MedicationAdministrationPerformer> providers = new ArrayList<>();
        if (request.getProviders() != null) {
            for (MedicationAdministrationPerformerRequest performer : request.getProviders()) {
                MedicationAdministrationPerformer newProvider = new MedicationAdministrationPerformer();
                newProvider.setUuid(performer.getUuid());
                newProvider.setActor(Context.getProviderService().getProviderByUuid(performer.getProviderUuid()));
                newProvider.setFunction(Context.getConceptService().getConceptByName(performer.getFunction()));
                providers.add(newProvider);
            }
            if (existingMedicationAdministration !=null && existingMedicationAdministration.getPerformers() !=null){
                providers.addAll(existingMedicationAdministration.getPerformers());
            }
        }
        medicationAdministration.setPerformers(new HashSet<>(providers));
        List<MedicationAdministrationNote> notes = new ArrayList<>();
        if (request.getNotes() != null) {
            for (MedicationAdministrationNoteRequest note : request.getNotes()) {
                MedicationAdministrationNote newNote = new MedicationAdministrationNote();
                newNote.setUuid(note.getUuid());
                newNote.setAuthor(Context.getProviderService().getProviderByUuid(note.getAuthorUuid()));
                newNote.setText(note.getText());
                newNote.setRecordedTime(note.getRecordedTimeAsLocaltime());
                notes.add(newNote);
            }
            if (existingMedicationAdministration !=null && existingMedicationAdministration.getNotes() !=null){
                notes.addAll(existingMedicationAdministration.getNotes());
            }
        }
        medicationAdministration.setNotes(new HashSet<>(notes));
        return medicationAdministration;
    }

    public MedicationAdministrationResponse mapMedicationAdministrationToResponse(org.hl7.fhir.r4.model.MedicationAdministration fhirMedicationAdministration) {
        MedicationAdministration openmrsMedicationAdministration = (MedicationAdministration) medicationAdministrationTranslator.toOpenmrsType(fhirMedicationAdministration);
        MedicationAdministrationResponse response =  MedicationAdministrationResponse.createFrom(openmrsMedicationAdministration);
        return response;
    }


}
