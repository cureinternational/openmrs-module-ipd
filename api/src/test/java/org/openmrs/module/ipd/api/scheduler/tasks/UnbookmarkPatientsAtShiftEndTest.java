package org.openmrs.module.ipd.api.scheduler.tasks;

import org.junit.Test;
import java.lang.reflect.Method;
import java.util.List;

import static org.junit.Assert.*;

/**
 * Unit tests for UnbookmarkPatientsAtShiftEnd scheduler task.
 * Uses reflection to test private helper methods without changing access specifiers.
 */
public class UnbookmarkPatientsAtShiftEndTest {

    private final UnbookmarkPatientsAtShiftEnd task = new UnbookmarkPatientsAtShiftEnd();

    /**
     * Test: Parse standard shift configuration
     */
    @Test
    public void shouldParseStandardDayShiftConfiguration() throws Exception {
        String shiftJson = "{\"1\": {\"shiftStartTime\":\"08:00\",\"shiftEndTime\":\"19:00\"}}";
        List<String> shiftTimes = invokeParseShiftEndTimes(shiftJson);

        assertNotNull("Should parse shift times", shiftTimes);
        assertEquals("Should have 1 shift", 1, shiftTimes.size());
        assertEquals("Should parse 19:00", "19:00", shiftTimes.get(0));
    }

    /**
     * Edge Case: Parse overnight shift (19:00 to 08:00 next day)
     */
    @Test
    public void shouldParseOvernightShiftConfiguration() throws Exception {
        String overnightJson = "{\"1\": {\"shiftStartTime\":\"19:00\",\"shiftEndTime\":\"08:00\"}}";
        List<String> shiftTimes = invokeParseShiftEndTimes(overnightJson);

        assertEquals("Should parse overnight shift", 1, shiftTimes.size());
        assertEquals("Should have 08:00 as shift end", "08:00", shiftTimes.get(0));
    }

    /**
     * Edge Case: Parse multiple shifts
     */
    @Test
    public void shouldParseMultipleShiftPatterns() throws Exception {
        String multiShiftJson = "{" +
            "\"1\": {\"shiftStartTime\":\"08:00\",\"shiftEndTime\":\"16:00\"}," +
            "\"2\": {\"shiftStartTime\":\"16:00\",\"shiftEndTime\":\"00:00\"}," +
            "\"3\": {\"shiftStartTime\":\"00:00\",\"shiftEndTime\":\"08:00\"}" +
        "}";

        List<String> shiftTimes = invokeParseShiftEndTimes(multiShiftJson);

        assertEquals("Should parse 3 shifts", 3, shiftTimes.size());
        assertTrue("Should contain 16:00", shiftTimes.contains("16:00"));
        assertTrue("Should contain 00:00", shiftTimes.contains("00:00"));
        assertTrue("Should contain 08:00", shiftTimes.contains("08:00"));
    }

    /**
     * Test: Handle null configuration
     */
    @Test
    public void shouldHandleNullShiftConfiguration() throws Exception {
        List<String> shiftTimes = invokeParseShiftEndTimes(null);
        assertEquals("Should return empty list for null", 0, shiftTimes.size());
    }

    /**
     * Test: Handle empty configuration
     */
    @Test
    public void shouldHandleEmptyShiftConfiguration() throws Exception {
        List<String> shiftTimes = invokeParseShiftEndTimes("");
        assertEquals("Should return empty list for empty string", 0, shiftTimes.size());
    }

    /**
     * Test: Validate time format
     */
    @Test
    public void shouldValidateTimeFormat() throws Exception {
        assertTrue("Should accept valid time 08:00", invokeIsValidTimeFormat("08:00"));
        assertTrue("Should accept valid time 19:00", invokeIsValidTimeFormat("19:00"));
        assertTrue("Should accept valid time 00:00", invokeIsValidTimeFormat("00:00"));
        assertTrue("Should accept valid time 23:59", invokeIsValidTimeFormat("23:59"));

        assertFalse("Should reject invalid time 25:00", invokeIsValidTimeFormat("25:00"));
        assertFalse("Should reject invalid time 19:60", invokeIsValidTimeFormat("19:60"));
        assertFalse("Should reject invalid time 19", invokeIsValidTimeFormat("19"));
        assertFalse("Should reject null", invokeIsValidTimeFormat(null));
    }

    /**
     * Test: Convert time string to minutes
     */
    @Test
    public void shouldConvertTimeStringToMinutes() throws Exception {
        assertEquals("08:00 should be 480 minutes", 480, invokeTimeStringToMinutes("08:00"));
        assertEquals("19:00 should be 1140 minutes", 1140, invokeTimeStringToMinutes("19:00"));
        assertEquals("00:00 should be 0 minutes", 0, invokeTimeStringToMinutes("00:00"));
        assertEquals("23:59 should be 1439 minutes", 1439, invokeTimeStringToMinutes("23:59"));

        assertEquals("Invalid time should return -1", -1, invokeTimeStringToMinutes("invalid"));
        assertEquals("Empty string should return -1", -1, invokeTimeStringToMinutes(""));
    }


    @Test
    public void shouldHandleShiftAtMidnight() throws Exception {
        String midnightJson = "{\"1\": {\"shiftStartTime\":\"20:00\",\"shiftEndTime\":\"00:00\"}}";
        List<String> shiftTimes = invokeParseShiftEndTimes(midnightJson);

        assertEquals("Should parse midnight shift", 1, shiftTimes.size());
        assertEquals("Should have 00:00 as shift end", "00:00", shiftTimes.get(0));
        assertEquals("Midnight in minutes should be 0", 0, invokeTimeStringToMinutes("00:00"));
    }

    @Test
    public void shouldIgnoreInvalidShiftTimesInConfig() throws Exception {
        String mixedJson = "{" +
            "\"1\": {\"shiftStartTime\":\"08:00\",\"shiftEndTime\":\"16:00\"}," +
            "\"2\": {\"shiftStartTime\":\"invalid\",\"shiftEndTime\":\"25:00\"}," +
            "\"3\": {\"shiftStartTime\":\"19:00\",\"shiftEndTime\":\"08:00\"}" +
        "}";

        List<String> shiftTimes = invokeParseShiftEndTimes(mixedJson);

        assertEquals("Should only include valid shifts", 2, shiftTimes.size());
        assertTrue("Should have 16:00", shiftTimes.contains("16:00"));
        assertTrue("Should have 08:00", shiftTimes.contains("08:00"));
        assertFalse("Should not have 25:00", shiftTimes.contains("25:00"));
    }

    @Test
    public void shouldCalculate13HourShiftDuration() throws Exception {
        int startMinutes = invokeTimeStringToMinutes("19:00");
        int endMinutes = invokeTimeStringToMinutes("08:00");

        int overnightDuration = (24 * 60 - startMinutes) + endMinutes;

        assertEquals("19:00 to 08:00 should be 13 hours", 13 * 60, overnightDuration);
    }

    /**
     * Test: Verify error handling for malformed JSON
     */
    @Test
    public void shouldHandleMalformedJson() throws Exception {
        String malformedJson = "{invalid json}";
        List<String> shiftTimes = invokeParseShiftEndTimes(malformedJson);

        assertEquals("Should return empty list for malformed JSON", 0, shiftTimes.size());
    }

    // Reflection helper methods
    private List<String> invokeParseShiftEndTimes(String json) throws Exception {
        Method method = UnbookmarkPatientsAtShiftEnd.class.getDeclaredMethod("parseShiftEndTimes", String.class);
        method.setAccessible(true);
        return (List<String>) method.invoke(task, json);
    }

    private boolean invokeIsValidTimeFormat(String time) throws Exception {
        Method method = UnbookmarkPatientsAtShiftEnd.class.getDeclaredMethod("isValidTimeFormat", String.class);
        method.setAccessible(true);
        return (Boolean) method.invoke(task, time);
    }

    private int invokeTimeStringToMinutes(String timeStr) throws Exception {
        Method method = UnbookmarkPatientsAtShiftEnd.class.getDeclaredMethod("timeStringToMinutes", String.class);
        method.setAccessible(true);
        return (Integer) method.invoke(task, timeStr);
    }
}
