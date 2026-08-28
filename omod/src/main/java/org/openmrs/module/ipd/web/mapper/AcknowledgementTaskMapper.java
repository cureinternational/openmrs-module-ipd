package org.openmrs.module.ipd.web.mapper;

import org.openmrs.Concept;
import org.openmrs.ConceptClass;
import org.openmrs.ConceptSearchResult;
import org.openmrs.ConceptSet;
import org.openmrs.Patient;
import org.openmrs.Provider;
import org.openmrs.api.APIException;
import org.openmrs.api.context.Context;
import org.openmrs.module.fhir2.model.FhirReference;
import org.openmrs.module.fhir2.model.FhirTask;
import org.openmrs.module.fhirExtension.model.FhirTaskRequestedPeriod;
import org.openmrs.module.fhirExtension.model.Task;
import org.openmrs.module.ipd.api.model.MedicationAdministrationNote;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Mapper for creating FHIR Tasks for medication administration acknowledgements.
 * This mapper is specific to the IPD module's acknowledgement workflow and is
 * intentionally kept independent of the shared fhir2Extension TaskMapper.
 */
@Component
public class AcknowledgementTaskMapper {

    private static final String ALL_TASK_TYPES = "All Task Types";

    /**
     * Creates a FHIR Task for acknowledging a medication administration note.
     *
     * @param noteUuid the UUID of the note being acknowledged (set as the task's focus)
     * @param patientUuid the UUID of the patient (set as the task's beneficiary/"for")
     * @param taskName the name/identifier for the task
     * @param taskType the concept name identifying the task's type, resolved against the
     *                 "All Task Types" concept set (configured via a global property)
     * @param remarks the acknowledgement remarks/comment
     * @param ownerUuid the UUID of the Provider who is the owner of this task (optional)
     * @return a Task object ready to be saved
     */
    public Task createAcknowledgementTask(String noteUuid, String patientUuid, String taskName,
                                           String taskType, String remarks, String ownerUuid) {
        Task task = new Task();
        FhirTask fhirTask = new FhirTask();

        fhirTask.setUuid(UUID.randomUUID().toString());
        fhirTask.setName(taskName);
        fhirTask.setStatus(FhirTask.TaskStatus.COMPLETED);
        fhirTask.setIntent(FhirTask.TaskIntent.ORDER);
        fhirTask.setComment(remarks);
        fhirTask.setTaskCode(getConceptForTaskType(taskType));

        if (patientUuid != null) {
            FhirReference forReference = new FhirReference();
            forReference.setType(Patient.class.getTypeName());
            forReference.setReference(Patient.class.getTypeName() + "/" + patientUuid);
            forReference.setTargetUuid(patientUuid);
            fhirTask.setForReference(forReference);
        }

        FhirReference focusReference = new FhirReference();
        focusReference.setType(MedicationAdministrationNote.class.getTypeName());
        focusReference.setReference(MedicationAdministrationNote.class.getTypeName() + "/" + noteUuid);
        focusReference.setTargetUuid(noteUuid);
        fhirTask.setFocusReference(focusReference);

        if (ownerUuid != null) {
            FhirReference ownerReference = new FhirReference();
            ownerReference.setType(Provider.class.getTypeName());
            ownerReference.setReference(Provider.class.getTypeName() + "/" + ownerUuid);
            ownerReference.setTargetUuid(ownerUuid);
            fhirTask.setOwnerReference(ownerReference);
        }

        FhirTaskRequestedPeriod requestedPeriod = new FhirTaskRequestedPeriod();
        requestedPeriod.setTask(fhirTask);
        Date now = new Date();
        requestedPeriod.setRequestedStartTime(now);
        requestedPeriod.setRequestedEndTime(now);

        task.setFhirTask(fhirTask);
        task.setFhirTaskRequestedPeriod(requestedPeriod);

        return task;
    }

    private Concept getConceptForTaskType(String taskType) {
        if (taskType == null || taskType.isEmpty()) {
            return null;
        }
        List<ConceptClass> parentConceptClasses = new ArrayList<>();
        parentConceptClasses.add(Context.getConceptService().getConceptClassByName("ConvSet"));
        List<Locale> locales = Arrays.asList(Locale.ENGLISH);
        List<ConceptSearchResult> conceptsSearchResult = Context.getConceptService()
                .getConcepts(ALL_TASK_TYPES, locales, false, parentConceptClasses, null, null, null, null, 0, null);
        Concept taskTypeConcept = conceptsSearchResult.stream()
                .map(ConceptSearchResult::getConcept)
                .filter(concept -> concept != null)
                .flatMap(concept -> concept.getConceptSets().stream().map(ConceptSet::getConcept))
                .filter(concept -> concept.getNames(false) != null &&
                        concept.getNames(false).stream().anyMatch(name -> name.getName().equals(taskType)))
                .findFirst()
                .orElse(null);
        if (taskTypeConcept == null) {
            throw new APIException("Could not resolve task type '" + taskType
                    + "' against the '" + ALL_TASK_TYPES + "' concept set. Check the global property value and the concept set configuration.");
        }
        return taskTypeConcept;
    }
}
