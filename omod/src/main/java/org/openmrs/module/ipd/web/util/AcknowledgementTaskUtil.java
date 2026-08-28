package org.openmrs.module.ipd.web.util;

import org.openmrs.module.fhir2.model.FhirTask;
import org.openmrs.module.fhirExtension.model.Task;
import org.openmrs.module.fhirExtension.model.TaskSearchRequest;
import org.openmrs.module.fhirExtension.service.TaskService;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Shared task-search and focus-UUID-match logic for medication administration note
 * acknowledgement tasks, used by both the service layer (lock checks) and the response
 * DTOs (rendering acknowledgement state) to avoid duplicating the same query/matching logic.
 */
public final class AcknowledgementTaskUtil {

    public static final String ACKNOWLEDGE_TASK_NAME = "ACKNOWLEDGE_MEDICATION_NOTE";

    private AcknowledgementTaskUtil() {
    }

    public static List<Task> searchCompletedAcknowledgementTasks(TaskService taskService) {
        TaskSearchRequest searchRequest = new TaskSearchRequest();
        searchRequest.setTaskName(Collections.singletonList(ACKNOWLEDGE_TASK_NAME));
        searchRequest.setTaskStatus(Collections.singletonList(FhirTask.TaskStatus.COMPLETED));
        return taskService.searchTasks(searchRequest);
    }

    public static String getFocusNoteUuid(Task task) {
        if (task == null || task.getFhirTask() == null || task.getFhirTask().getFocusReference() == null) {
            return null;
        }
        return task.getFhirTask().getFocusReference().getTargetUuid();
    }

    public static Map<String, Task> mapCompletedTasksByNoteUuid(TaskService taskService, Collection<String> noteUuids) {
        Map<String, Task> result = new HashMap<>();
        if (noteUuids == null || noteUuids.isEmpty()) {
            return result;
        }
        Set<String> uuidSet = new HashSet<>(noteUuids);
        for (Task task : searchCompletedAcknowledgementTasks(taskService)) {
            String targetUuid = getFocusNoteUuid(task);
            if (targetUuid != null && uuidSet.contains(targetUuid)) {
                result.put(targetUuid, task);
            }
        }
        return result;
    }

    public static boolean isAnyNoteAcknowledged(TaskService taskService, Collection<String> noteUuids) {
        return !mapCompletedTasksByNoteUuid(taskService, noteUuids).isEmpty();
    }
}
