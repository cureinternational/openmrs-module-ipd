package org.openmrs.module.ipd.web.mapper;

import org.openmrs.Concept;
import org.openmrs.Encounter;
import org.openmrs.Provider;
import org.openmrs.api.context.Context;
import org.openmrs.module.fhir2.model.FhirReference;
import org.openmrs.module.fhir2.model.FhirTask;
import org.openmrs.module.fhirExtension.model.FhirTaskRequestedPeriod;
import org.openmrs.module.fhirExtension.model.Task;
import org.openmrs.module.ipd.api.model.MedicationAdministrationNote;
import org.springframework.stereotype.Component;

import java.util.Date;
import java.util.UUID;

/**
 * Mapper for creating FHIR Tasks for medication administration acknowledgements.
 * This mapper is specific to the IPD module's acknowledgement workflow.
 */
@Component
public class AcknowledgementTaskMapper {

    private static final String ACKNOWLEDGE_TASK_TYPE = "acknowledge_amend_note";

    /**
     * Creates a FHIR Task for acknowledging a medication administration note.
     *
     * @param noteUuid the UUID of the note being acknowledged
     * @param encounterUuid the encounter UUID (optional)
     * @param taskName the name/identifier for the task
     * @param remarks the acknowledgement remarks/comment
     * @param ownerUuid the UUID of the Provider who is the owner of this task (optional)
     * @return a Task object ready to be saved
     */
    public Task createAcknowledgementTask(String noteUuid, String encounterUuid,
                                          String taskName, String remarks, String ownerUuid) {
        Task task = new Task();
        FhirTask fhirTask = new FhirTask();

        // Set basic task properties
        fhirTask.setUuid(UUID.randomUUID().toString());
        fhirTask.setName(taskName);
        fhirTask.setStatus(FhirTask.TaskStatus.COMPLETED);
        fhirTask.setIntent(FhirTask.TaskIntent.ORDER);
        fhirTask.setComment(remarks);

        // Set task type concept
        fhirTask.setTaskCode(getAcknowledgeNoteConcept());

        // Set forReference to point to the note being acknowledged
        FhirReference forReference = new FhirReference();
        forReference.setType(MedicationAdministrationNote.class.getTypeName());
        forReference.setReference(MedicationAdministrationNote.class.getTypeName() + "/" + noteUuid);
        forReference.setTargetUuid(noteUuid);
        fhirTask.setForReference(forReference);

        // Set encounter reference if provided
        if (encounterUuid != null) {
            FhirReference encounterReference = new FhirReference();
            encounterReference.setType(Encounter.class.getTypeName());
            encounterReference.setReference(Encounter.class.getTypeName() + "/" + encounterUuid);
            encounterReference.setTargetUuid(encounterUuid);
            fhirTask.setEncounterReference(encounterReference);
        }

        // Set owner reference if provided
        if (ownerUuid != null) {
            FhirReference ownerReference = new FhirReference();
            ownerReference.setType(Provider.class.getTypeName());
            ownerReference.setReference(Provider.class.getTypeName() + "/" + ownerUuid);
            ownerReference.setTargetUuid(ownerUuid);
            fhirTask.setOwnerReference(ownerReference);
        }

        // Set requested period
        FhirTaskRequestedPeriod requestedPeriod = new FhirTaskRequestedPeriod();
        requestedPeriod.setTask(fhirTask);
        requestedPeriod.setRequestedStartTime(new Date());
        requestedPeriod.setRequestedEndTime(new Date());

        task.setFhirTask(fhirTask);
        task.setFhirTaskRequestedPeriod(requestedPeriod);

        return task;
    }

    /**
     * Gets or creates the ACKNOWLEDGE_NOTE task type concept.
     *
     * @return the Concept for ACKNOWLEDGE_NOTE task type, or null if not found
     */
    private Concept getAcknowledgeNoteConcept() {
        // Try to find the ACKNOWLEDGE_NOTE concept from "All Task Types" concept set
        try {
            Concept allTaskTypes = Context.getConceptService().getConceptByName("All Task Types");
            if (allTaskTypes != null && allTaskTypes.getConceptSets() != null) {
                return allTaskTypes.getConceptSets().stream()
                    .map(cs -> cs.getConcept())
                    .filter(concept -> concept.getNames().stream()
                        .anyMatch(name -> ACKNOWLEDGE_TASK_TYPE.equals(name.getName())))
                    .findFirst()
                    .orElse(null);
            }
        } catch (Exception e) {
            // If concept not found, return null - task will still be created without taskCode
        }
        return null;
    }
}
