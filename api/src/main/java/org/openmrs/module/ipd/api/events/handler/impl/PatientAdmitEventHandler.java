package org.openmrs.module.ipd.api.events.handler.impl;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.openmrs.module.fhirExtension.model.Task;
import org.openmrs.module.fhirExtension.service.TaskService;
import org.openmrs.module.fhirExtension.web.contract.TaskInputRequestDTO;
import org.openmrs.module.fhirExtension.web.contract.TaskRequest;
import org.openmrs.module.fhirExtension.web.mapper.TaskMapper;
import org.openmrs.module.ipd.api.events.ConfigLoader;
import org.openmrs.module.ipd.api.events.IPDEventUtils;
import org.openmrs.module.ipd.api.events.model.ConfigDetail;
import org.openmrs.module.ipd.api.events.model.IPDEvent;
import org.openmrs.module.ipd.api.events.model.TaskDetail;
import org.openmrs.module.ipd.api.events.handler.IPDEventHandler;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Component
public class PatientAdmitEventHandler  implements IPDEventHandler {

    private final Log log = LogFactory.getLog(this.getClass());

    @Autowired
    ConfigLoader configLoader;

    @Autowired
    private TaskMapper taskMapper;

    @Autowired
    private TaskService taskService;

    @Override
    public void handleEvent(IPDEvent event) {
        List<ConfigDetail> configList = configLoader.getConfigs();
        ConfigDetail eventConfig = configList.stream()
                .filter(config -> config.getEvent().equals(event.getIpdEventType().name()))
                .findFirst()
                .orElse(null);

        if (eventConfig != null) {
            for(TaskDetail taskDetail : eventConfig.getTasks()) {
                List<TaskInputRequestDTO> taskInputs = taskDetail.getInput() != null ? taskDetail.getInput().stream()
                        .map(input -> {
                            TaskInputRequestDTO dto = new TaskInputRequestDTO();
                            dto.setTypeUuid(input.getTypeUuid());
                            dto.setValueText(input.getValueText());
                            return dto;
                        })
                        .collect(Collectors.toList()) : Collections.emptyList();

                TaskRequest taskRequest = IPDEventUtils.createNonMedicationTaskRequest(event, taskDetail.getName(), taskDetail.getType(), taskInputs, true);
                Task task = taskMapper.fromRequest(taskRequest);
                taskService.saveTask(task);
                log.info("Task created " + taskDetail.getName());
            }
        }
    }
}
