package org.openmrs.module.ipd.api.scheduler.tasks;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import org.openmrs.api.context.Context;
import org.openmrs.module.ipd.api.service.CareTeamService;
import org.openmrs.scheduler.TaskDefinition;
import org.openmrs.scheduler.tasks.AbstractTask;

public class UnbookmarkPatientsAtShiftEnd extends AbstractTask {

    private static final String SHIFT_DETAILS_GP = "ipd.shiftDetails";
    private static final int TOLERANCE_MINUTES = 1;
    private static final boolean TEST_MODE = true; // Set to true to test unbooking immediately (only for testing)

    @Override
    public void execute() {
        try {
            // Fetch from Global Property - Liquibase migration ensures it exists
            String shiftDetailsJson = Context.getAdministrationService()
                .getGlobalProperty(SHIFT_DETAILS_GP);

            // If property doesn't exist, skip execution (fallback relies on Liquibase to create it)
            if (shiftDetailsJson == null || shiftDetailsJson.isEmpty()) {
                return;
            }

            List<String> shiftEndTimes = parseShiftEndTimes(shiftDetailsJson);

            // Safety check: Only unbookmark if we're actually at/near shift end time
            // This prevents accidental unbooking at startup before scheduler reschedules the task
            if (TEST_MODE || isShiftEndTime(shiftEndTimes)) {
                CareTeamService careTeamService = Context.getService(CareTeamService.class);
                careTeamService.unbookmarkAllActivePatients();
            }

            scheduleNextExecution(shiftEndTimes);
        } catch (Exception e) {
            // Silently handle all exceptions to prevent breaking OpenMRS
        }
    }

    private boolean isShiftEndTime(List<String> times) {
        try {
            Calendar now = Calendar.getInstance();
            int nowTotal = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE);
            for (String t : times) {
                try {
                    String[] parts = t.split(":");
                    if (parts.length == 2) {
                        int total = Integer.parseInt(parts[0]) * 60 + Integer.parseInt(parts[1]);
                        // Check if we're within 1 minute of shift end time (safety guard against accidental unbooking)
                        if (Math.abs(total - nowTotal) <= TOLERANCE_MINUTES) return true;
                    }
                } catch (NumberFormatException e) {
                    // Skip invalid time format
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
            // Simple string parsing for JSON object of shift objects
            // Expected format: {"1": {"shiftStartTime":"08:00","shiftEndTime":"19:00"},"2": {...}}
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
            // Fallback to default ET times
        }
        if (times.isEmpty()) {
            times.add("19:00");
            times.add("08:00");
        }
        return times;
    }

private void scheduleNextExecution(List<String> times) {
        try {
            Date now = new Date();
            Date earliest = null;
            for (String t : times) {
                try {
                    String[] parts = t.split(":");
                    if (parts.length == 2) {
                        Calendar cal = Calendar.getInstance();
                        cal.set(Calendar.HOUR_OF_DAY, Integer.parseInt(parts[0]));
                        cal.set(Calendar.MINUTE, Integer.parseInt(parts[1]));
                        cal.set(Calendar.SECOND, 0);
                        cal.set(Calendar.MILLISECOND, 0);
                        // Schedule to run 1 minute BEFORE shift end time to ensure participants are still active
                        cal.add(Calendar.MINUTE, -1);
                        Date candidate = cal.getTime();
                        if (!candidate.after(now)) {
                            cal.add(Calendar.DAY_OF_MONTH, 1);
                            candidate = cal.getTime();
                        }
                        if (earliest == null || candidate.before(earliest)) earliest = candidate;
                    }
                } catch (NumberFormatException e) {
                    // Skip invalid time format
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
            // Silently handle
        }
    }
}
