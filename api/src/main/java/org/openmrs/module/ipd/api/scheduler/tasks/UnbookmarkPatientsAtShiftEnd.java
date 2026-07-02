package org.openmrs.module.ipd.api.scheduler.tasks;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import org.openmrs.api.context.Context;
import org.openmrs.module.ipd.api.service.CareTeamService;
import org.openmrs.scheduler.TaskDefinition;
import org.openmrs.scheduler.tasks.AbstractTask;

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
        List<String> times = new ArrayList<>();
        try {
            int index = 0;
            while (index < json.length()) {
                int startIdx = json.indexOf("\"shiftEndTime\":", index);
                if (startIdx == -1) break;
                int quoteStart = json.indexOf("\"", startIdx + 15);
                int quoteEnd = json.indexOf("\"", quoteStart + 1);
                if (quoteStart != -1 && quoteEnd != -1) {
                    String endTime = json.substring(quoteStart + 1, quoteEnd);
                    times.add(endTime);
                    index = quoteEnd + 1;
                } else {
                    break;
                }
            }
        } catch (Exception e) {
            // Error parsing shift details
        }
        return times;
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
                    Context.getSchedulerService().saveTaskDefinition(taskDef);
                }
            }
        } catch (Exception e) {
            // Silently handle exceptions
        }
    }
}
