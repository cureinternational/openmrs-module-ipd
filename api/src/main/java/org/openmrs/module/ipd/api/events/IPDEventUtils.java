package org.openmrs.module.ipd.api.events;

import org.openmrs.module.fhir2.model.FhirTask;
import org.openmrs.module.fhirExtension.web.contract.TaskInputDTO;
import org.openmrs.module.fhirExtension.web.contract.TaskRequest;
import org.openmrs.module.ipd.api.events.model.IPDEvent;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class IPDEventUtils {

    public static TaskRequest createNonMedicationTaskRequest(IPDEvent ipdEvent, String name, String taskType, Boolean isSystemGenerated) {
        List<TaskInputDTO> emptyInput = new ArrayList<>();
        TaskRequest taskRequest = new TaskRequest();
        taskRequest.setName(name);
        taskRequest.setTaskType(taskType);
        taskRequest.setInput(emptyInput);
        taskRequest.setEncounterUuid(ipdEvent.getEncounterUuid());
        taskRequest.setPatientUuid(ipdEvent.getPatientUuid());
        taskRequest.setRequestedStartTime(new Date());
        taskRequest.setIntent(FhirTask.TaskIntent.ORDER);
        taskRequest.setStatus(FhirTask.TaskStatus.REQUESTED);
        taskRequest.setIsSystemGeneratedTask(isSystemGenerated);
        return taskRequest;
    }

    public static TaskRequest createNonMedicationTaskRequest(IPDEvent ipdEvent, String name, String taskType, List<TaskInputDTO> input, Boolean isSystemGenerated) {
        TaskRequest taskRequest = new TaskRequest();
        taskRequest.setName(name);
        taskRequest.setTaskType(taskType);
        taskRequest.setInput(input != null ? input : new ArrayList<>());
        taskRequest.setEncounterUuid(ipdEvent.getEncounterUuid());
        taskRequest.setPatientUuid(ipdEvent.getPatientUuid());
        taskRequest.setRequestedStartTime(new Date());
        taskRequest.setIntent(FhirTask.TaskIntent.ORDER);
        taskRequest.setStatus(FhirTask.TaskStatus.REQUESTED);
        taskRequest.setIsSystemGeneratedTask(isSystemGenerated);
        return taskRequest;
    }
}
