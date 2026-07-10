package org.openmrs.module.ipd.api.scheduler.tasks;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import org.openmrs.api.context.Context;
import org.openmrs.module.ipd.api.service.CareTeamService;
import org.openmrs.scheduler.TaskDefinition;
import org.openmrs.scheduler.tasks.AbstractTask;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Self-rescheduling scheduler task that unbookmarks all active patients at shift end times.
 *
 * Uses OpenMRS scheduler's repeatInterval=0 pattern for self-rescheduling:
 * - Task fires at calculated startTime (1 minute before shift end)
 * - After execution, calculates next shift end time and reschedules itself
 * - repeatInterval=0 ensures the task doesn't auto-repeat; manual rescheduling via setStartTime()
 *
 * Shift times are configured via Global Property 'ipd.shiftDetails' in JSON format.
 */
public class UnbookmarkPatientsAtShiftEnd extends AbstractTask {

    private static final Logger logger = LoggerFactory.getLogger(UnbookmarkPatientsAtShiftEnd.class);
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final String SHIFT_DETAILS_GP = "ipd.shiftDetails";
    private static final int EXECUTION_BUFFER_MINUTES = 1;

    @Override
    public void execute() {
        try {
            String shiftDetailsJson = Context.getAdministrationService()
                .getGlobalProperty(SHIFT_DETAILS_GP);

            if (shiftDetailsJson == null || shiftDetailsJson.isEmpty()) {
                return;
            }

            List<String> shiftEndTimes = parseShiftEndTimes(shiftDetailsJson);

            if (shiftEndTimes.isEmpty()) {
                return;
            }

            if (isShiftEndTime(shiftEndTimes)) {
                CareTeamService careTeamService = Context.getService(CareTeamService.class);
                careTeamService.unbookmarkAllActivePatients();
            }

            scheduleNextExecution(shiftEndTimes);
        } catch (Exception e) {
            // Silently handle exceptions
        }
    }

    private int timeStringToMinutes(String timeStr) {
        try {
            String[] parts = timeStr.split(":");
            if (parts.length == 2) {
                return Integer.parseInt(parts[0]) * 60 + Integer.parseInt(parts[1]);
            }
        } catch (NumberFormatException e) {
            // Invalid time format
        }
        return -1;
    }

    private boolean isShiftEndTime(List<String> times) {
        try {
            Calendar now = Calendar.getInstance();
            int nowMinutes = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE);
            for (String t : times) {
                int shiftEndMinutes = timeStringToMinutes(t);
                if (shiftEndMinutes != -1) {
                    if (Math.abs(shiftEndMinutes - nowMinutes) <= EXECUTION_BUFFER_MINUTES) {
                        return true;
                    }
                }
            }
        } catch (Exception e) {
            // Silently handle
        }
        return false;
    }

    private List<String> parseShiftEndTimes(String json) {
        try {
            JsonNode root = OBJECT_MAPPER.readTree(json);
            List<String> times = new ArrayList<>();

            if (root.isObject()) {
                for (JsonNode shift : root) {
                    JsonNode endTime = shift.get("shiftEndTime");
                    if (endTime != null && endTime.isTextual() && isValidTimeFormat(endTime.asText())) {
                        times.add(endTime.asText());
                    }
                }
            }

            if (times.isEmpty()) {
                logger.warn("No valid shiftEndTime entries found in Global Property '{}'", SHIFT_DETAILS_GP);
            }
            return times;
        } catch (Exception e) {
            logger.error("Failed to parse Global Property '{}' as JSON. Value was: '{}'",
                SHIFT_DETAILS_GP, json, e);
            return new ArrayList<>();
        }
    }

    private boolean isValidTimeFormat(String time) {
        return time != null && time.matches("^([01]\\d|2[0-3]):[0-5]\\d$");
    }

    private Date getExecutionTime(String shiftEndTime) {
        int shiftEndMinutes = timeStringToMinutes(shiftEndTime);
        if (shiftEndMinutes == -1) {
            return null;
        }
        try {
            Calendar cal = Calendar.getInstance();
            cal.set(Calendar.HOUR_OF_DAY, shiftEndMinutes / 60);
            cal.set(Calendar.MINUTE, shiftEndMinutes % 60);
            cal.set(Calendar.SECOND, 0);
            cal.set(Calendar.MILLISECOND, 0);
            cal.add(Calendar.MINUTE, -EXECUTION_BUFFER_MINUTES);
            return cal.getTime();
        } catch (Exception e) {
            return null;
        }
    }

    private void scheduleNextExecution(List<String> times) {
        try {
            Date now = new Date();
            Date earliest = null;
            for (String shiftEndTime : times) {
                Date executionTime = getExecutionTime(shiftEndTime);
                if (executionTime != null) {
                    if (!executionTime.after(now)) {
                        Calendar cal = Calendar.getInstance();
                        cal.setTime(executionTime);
                        cal.add(Calendar.DAY_OF_MONTH, 1);
                        executionTime = cal.getTime();
                    }
                    if (earliest == null || executionTime.before(earliest)) {
                        earliest = executionTime;
                    }
                }
            }
            if (earliest != null) {
                TaskDefinition taskDef = getTaskDefinition();
                if (taskDef != null) {
                    taskDef.setStartTime(earliest);
                    taskDef.setRepeatInterval(0L);
                    taskDef.setLastExecutionTime(new Date());
                    Context.getSchedulerService().rescheduleTask(taskDef);
                }
            }
        } catch (Exception e) {
            logger.error("Failed to reschedule UnbookmarkPatientsAtShiftEnd task", e);
        }
    }
}
