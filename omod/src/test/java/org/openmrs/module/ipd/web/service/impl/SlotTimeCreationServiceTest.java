package org.openmrs.module.ipd.web.service.impl;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;
import org.openmrs.DrugOrder;
import org.openmrs.module.ipd.api.model.Schedule;
import org.openmrs.module.ipd.api.model.Slot;
import org.openmrs.module.ipd.web.contract.CrossingSlotDTO;
import org.openmrs.module.ipd.web.contract.ScheduleMedicationRequest;
import org.openmrs.module.ipd.web.model.SlotTimeCreationResult;

import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import com.fasterxml.jackson.databind.ObjectMapper;

@RunWith(MockitoJUnitRunner.class)
public class SlotTimeCreationServiceTest {

    private SlotTimeCreationService slotTimeCreationService;

    @Before
    public void setUp() {
        slotTimeCreationService = new SlotTimeCreationService();
    }

    private ScheduleMedicationRequest buildFixedRequest(List<Long> dayWise) {
        return ScheduleMedicationRequest.builder()
                .medicationFrequency(ScheduleMedicationRequest.MedicationFrequency.FIXED_SCHEDULE_FREQUENCY)
                .dayWiseSlotsStartTime(dayWise)
                .build();
    }

    private DrugOrder createDrugOrderWithFixedSchedule(int sequence) throws Exception {
        DrugOrder order = org.mockito.Mockito.mock(DrugOrder.class, org.mockito.Mockito.withSettings().lenient());
        String dosingInstructions = String.format(
                "[{\"sequence\":%d,\"timing\":{\"repeat\":{\"count\":2},\"code\":{\"text\":\"Fixed Schedule\"}}}]",
                sequence
        );
        org.mockito.Mockito.when(order.getDosingInstructions()).thenReturn(dosingInstructions);
        org.mockito.Mockito.when(order.getUuid()).thenReturn("test-uuid-" + sequence);
        return order;
    }

    private Slot makeSlotWithBucketingOrder(int sequence, LocalDateTime startDateTime, DrugOrder order) {
        Slot slot = org.mockito.Mockito.mock(Slot.class, org.mockito.Mockito.withSettings().lenient());
        Schedule schedule = org.mockito.Mockito.mock(Schedule.class, org.mockito.Mockito.withSettings().lenient());

        org.mockito.Mockito.when(slot.getVariableDosageSequence()).thenReturn(sequence);
        org.mockito.Mockito.when(slot.getStartDateTime()).thenReturn(startDateTime);
        org.mockito.Mockito.when(slot.getSchedule()).thenReturn(schedule);
        org.mockito.Mockito.when(slot.getStatus()).thenReturn(Slot.SlotStatus.SCHEDULED);
        org.mockito.Mockito.when(slot.getMedicationAdministration()).thenReturn(null);
        org.mockito.Mockito.when(slot.getNotes()).thenReturn(null);
        org.mockito.Mockito.when(slot.getOriginDoseBucket()).thenReturn(null);
        org.mockito.Mockito.when(slot.getOrder()).thenReturn(order);

        return slot;
    }

    private List<Long> futureEpochList(int count) {
        LocalDateTime base = LocalDateTime.now().plusHours(1);
        Long[] epochs = new Long[count];
        for (int i = 0; i < count; i++) {
            epochs[i] = base.plusHours(i).toEpochSecond(ZoneOffset.UTC) * 1000L;
        }
        return Arrays.asList(epochs);
    }

    private DrugOrder buildDrugOrder(double quantity, double dose) {
        DrugOrder order = new DrugOrder();
        order.setQuantity(quantity);
        order.setDose(dose);
        return order;
    }

    private List<CrossingSlotDTO> crossingSlots(Boolean recurring, Slot.SourceBucket originDoseBucket, List<Long> epochs) {
        List<CrossingSlotDTO> crossingSlots = new ArrayList<>();
        for (Long epoch : epochs) {
            crossingSlots.add(CrossingSlotDTO.builder()
                    .epoch(epoch)
                    .isRecurringAcrossDays(recurring)
                    .originDoseBucket(originDoseBucket)
                    .build());
        }
        return crossingSlots;
    }


    @Test
    public void shouldUseQuantityDivDose_ForRegularOrders_FixedSchedule() {
        DrugOrder order = buildDrugOrder(6.0, 2.0);
        ScheduleMedicationRequest request = buildFixedRequest(futureEpochList(6));

        SlotTimeCreationResult result = slotTimeCreationService.createSlotsStartTimeFrom(request, order);

        assertNotNull("Result should not be null", result);
        assertNotNull("SlotsStartTime should not be null", result.getSlotsStartTime());
        assertEquals(3, result.getSlotsStartTime().size());
        assertTrue("Crossing tags should be empty for regular orders", result.getCrossingTagsByStartTime().isEmpty());
    }

    @Test
    public void shouldReturnEmptyResult_WhenNoTimeListsProvided() {
        DrugOrder order = buildDrugOrder(6.0, 2.0);
        ScheduleMedicationRequest request = ScheduleMedicationRequest.builder()
                .medicationFrequency(ScheduleMedicationRequest.MedicationFrequency.FIXED_SCHEDULE_FREQUENCY)
                .build();

        SlotTimeCreationResult result = slotTimeCreationService.createSlotsStartTimeFrom(request, order);

        assertNotNull("Result should not be null", result);
        assertEquals(0, result.getSlotsStartTime().size());
    }

    @Test
    public void shouldHandlePartialFirstDay_WithFixedSchedule() {
        DrugOrder order = buildDrugOrder(10.0, 2.0);
        List<Long> firstDay = futureEpochList(3);
        List<Long> dayWise = futureEpochList(2);
        ScheduleMedicationRequest request = ScheduleMedicationRequest.builder()
                .medicationFrequency(ScheduleMedicationRequest.MedicationFrequency.FIXED_SCHEDULE_FREQUENCY)
                .firstDaySlotsStartTime(firstDay)
                .dayWiseSlotsStartTime(dayWise)
                .build();

        SlotTimeCreationResult result = slotTimeCreationService.createSlotsStartTimeFrom(request, order);

        assertNotNull("Result should not be null", result);
        assertEquals(5, result.getSlotsStartTime().size());
    }

    @Test
    public void shouldHandleFirstDayDayWiseAndRemaining_WithFixedSchedule() {
        DrugOrder order = buildDrugOrder(12.0, 2.0);
        List<Long> firstDay = futureEpochList(2);
        List<Long> dayWise = futureEpochList(3);
        List<Long> remaining = futureEpochList(2);
        ScheduleMedicationRequest request = ScheduleMedicationRequest.builder()
                .medicationFrequency(ScheduleMedicationRequest.MedicationFrequency.FIXED_SCHEDULE_FREQUENCY)
                .firstDaySlotsStartTime(firstDay)
                .dayWiseSlotsStartTime(dayWise)
                .remainingDaySlotsStartTime(remaining)
                .build();

        SlotTimeCreationResult result = slotTimeCreationService.createSlotsStartTimeFrom(request, order);

        assertNotNull("Result should not be null", result);
        assertEquals(6, result.getSlotsStartTime().size());
    }

    @Test
    public void shouldRespectQuantityDose_WhenBothAreProvided() {
        DrugOrder order = buildDrugOrder(15.0, 3.0);
        List<Long> dayWise = futureEpochList(10);
        ScheduleMedicationRequest request = buildFixedRequest(dayWise);

        SlotTimeCreationResult result = slotTimeCreationService.createSlotsStartTimeFrom(request, order);

        assertNotNull("Result should not be null", result);
        assertEquals(5, result.getSlotsStartTime().size());
    }

    @Test
    public void shouldCreateSlots_WithCrossingTagsLookup_NotNull() {
        DrugOrder order = buildDrugOrder(4.0, 1.0);
        List<Long> dayWise = futureEpochList(4);
        ScheduleMedicationRequest request = buildFixedRequest(dayWise);

        SlotTimeCreationResult result = slotTimeCreationService.createSlotsStartTimeFrom(request, order);

        assertNotNull("Result should not be null", result);
        assertNotNull("Crossing tags lookup should not be null", result.getCrossingTagsByStartTime());
        assertEquals(4, result.getSlotsStartTime().size());
    }

    @Test
    public void shouldHandleFirstDayCrossingSlots_NonRecurring() {
        DrugOrder order = buildDrugOrder(4.0, 1.0);
        List<Long> firstDayTimes = futureEpochList(2);
        List<Long> firstDayCrossingTimes = futureEpochList(1);

        ScheduleMedicationRequest request = ScheduleMedicationRequest.builder()
                .medicationFrequency(ScheduleMedicationRequest.MedicationFrequency.FIXED_SCHEDULE_FREQUENCY)
                .firstDaySlotsStartTime(firstDayTimes)
                .crossingSlots(crossingSlots(false, Slot.SourceBucket.FIRST_DAY, firstDayCrossingTimes))
                .build();

        SlotTimeCreationResult result = slotTimeCreationService.createSlotsStartTimeFrom(request, order);

        assertNotNull("Result should not be null", result);
        assertEquals(2, result.getSlotsStartTime().size());
        assertEquals(1, result.getCrossingTagsByStartTime().size());
    }

    @Test
    public void shouldHandleDayWiseCrossingSlots_Recurring() {
        DrugOrder order = buildDrugOrder(6.0, 1.0);
        List<Long> dayWiseTimes = futureEpochList(2);
        List<Long> dayWiseCrossingTimes = futureEpochList(1);

        ScheduleMedicationRequest request = ScheduleMedicationRequest.builder()
                .medicationFrequency(ScheduleMedicationRequest.MedicationFrequency.FIXED_SCHEDULE_FREQUENCY)
                .dayWiseSlotsStartTime(dayWiseTimes)
                .crossingSlots(crossingSlots(true, Slot.SourceBucket.DAY_WISE, dayWiseCrossingTimes))
                .build();

        SlotTimeCreationResult result = slotTimeCreationService.createSlotsStartTimeFrom(request, order);

        assertNotNull("Result should not be null", result);
        assertEquals(6, result.getSlotsStartTime().size());
        assertTrue("Should have crossing tags for day-wise crossings", !result.getCrossingTagsByStartTime().isEmpty());
    }

    @Test
    public void shouldHandleMixedCrossingSlots() {
        DrugOrder order = buildDrugOrder(8.0, 1.0);
        List<Long> firstDay = futureEpochList(2);
        List<Long> firstDayCrossings = futureEpochList(1);
        List<Long> dayWise = futureEpochList(2);
        List<Long> dayWiseCrossings = futureEpochList(1);
        List<Long> remaining = futureEpochList(1);

        List<CrossingSlotDTO> crossingSlotContracts = new ArrayList<>();
        crossingSlotContracts.addAll(crossingSlots(false, Slot.SourceBucket.FIRST_DAY, firstDayCrossings));
        crossingSlotContracts.addAll(crossingSlots(true, Slot.SourceBucket.DAY_WISE, dayWiseCrossings));

        ScheduleMedicationRequest request = ScheduleMedicationRequest.builder()
                .medicationFrequency(ScheduleMedicationRequest.MedicationFrequency.FIXED_SCHEDULE_FREQUENCY)
                .firstDaySlotsStartTime(firstDay)
                .dayWiseSlotsStartTime(dayWise)
                .remainingDaySlotsStartTime(remaining)
                .crossingSlots(crossingSlotContracts)
                .build();

        SlotTimeCreationResult result = slotTimeCreationService.createSlotsStartTimeFrom(request, order);

        assertNotNull("Result should not be null", result);
        assertEquals(8, result.getSlotsStartTime().size());
        assertTrue("Should have crossing tags for both first-day and day-wise", !result.getCrossingTagsByStartTime().isEmpty());
    }

    @Test
    public void shouldHandleAllCrossingSlots() {
        DrugOrder order = buildDrugOrder(3.0, 1.0);
        List<Long> dayWiseCrossings = futureEpochList(3);

        ScheduleMedicationRequest request = ScheduleMedicationRequest.builder()
                .medicationFrequency(ScheduleMedicationRequest.MedicationFrequency.FIXED_SCHEDULE_FREQUENCY)
                .crossingSlots(crossingSlots(true, Slot.SourceBucket.DAY_WISE, dayWiseCrossings))
                .build();

        SlotTimeCreationResult result = slotTimeCreationService.createSlotsStartTimeFrom(request, order);

        assertNotNull("Result should not be null", result);
        assertEquals(3, result.getSlotsStartTime().size());
        assertEquals("All slots should be marked as crossing", 3, result.getCrossingTagsByStartTime().size());
    }

    @Test
    public void shouldHandleNullScheduleInReadPersistedCrossings() {
        List<Slot> slots = new ArrayList<>();
        Slot slot = new Slot();
        slot.setSchedule(null);
        slots.add(slot);

        List<org.openmrs.module.ipd.web.model.StageScheduleStatus> stages = slotTimeCreationService.buildStageSchedules(slots);

        assertNotNull("Should return empty list, not null", stages);
        assertEquals("Should be empty when schedule is null", 0, stages.size());
    }

    @Test
    public void shouldHandleEmptySlotList() {
        List<Slot> emptySlots = new ArrayList<>();

        List<org.openmrs.module.ipd.web.model.StageScheduleStatus> stages = slotTimeCreationService.buildStageSchedules(emptySlots);

        assertNotNull("Should return empty list, not null", stages);
        assertEquals("Should be empty for empty input", 0, stages.size());
    }

    @Test
    public void shouldHandleQuantityZeroOrNull() {
        DrugOrder order = new DrugOrder();
        order.setQuantity(null);
        order.setDose(2.0);
        List<Long> dayWise = futureEpochList(3);

        ScheduleMedicationRequest request = buildFixedRequest(dayWise);

        SlotTimeCreationResult result = slotTimeCreationService.createSlotsStartTimeFrom(request, order);

        assertNotNull("Result should not be null", result);
        assertEquals("Should infer from request when quantity is null", 3, result.getSlotsStartTime().size());
    }

    @Test
    public void shouldHandleDoseNull() {
        DrugOrder order = new DrugOrder();
        order.setQuantity(6.0);
        order.setDose(null);
        List<Long> dayWise = futureEpochList(3);

        ScheduleMedicationRequest request = buildFixedRequest(dayWise);

        SlotTimeCreationResult result = slotTimeCreationService.createSlotsStartTimeFrom(request, order);

        assertNotNull("Result should not be null", result);
        assertEquals("Should infer from request when dose is null", 3, result.getSlotsStartTime().size());
    }

    @Test
    public void shouldBucketSingleDay_AsDayWise() throws Exception {
        DrugOrder order = createDrugOrderWithFixedSchedule(1);
        Slot s1 = makeSlotWithBucketingOrder(1, LocalDateTime.of(2026, 8, 1, 8, 0), order);
        Slot s2 = makeSlotWithBucketingOrder(1, LocalDateTime.of(2026, 8, 1, 20, 0), order);

        List<org.openmrs.module.ipd.web.model.StageScheduleStatus> result =
                slotTimeCreationService.buildStageSchedules(Arrays.asList(s1, s2));

        assertNotNull("Result should not be null", result);
        assertFalse("Result should not be empty", result.isEmpty());
        assertNotNull("Single day should produce dayWise slots", result.get(0).getDayWiseSlotsStartTime());
        assertEquals("DayWise should have 2 slots", 2, result.get(0).getDayWiseSlotsStartTime().size());
        assertNull("Should not have firstDay slots for single day", result.get(0).getFirstDaySlotsStartTime());
        assertNull("Should not have remaining slots for single day", result.get(0).getRemainingDaySlotsStartTime());
    }

    @Test
    public void shouldBucketNonUniformDays_AsFirstDayAndRemaining() throws Exception {
        DrugOrder order = createDrugOrderWithFixedSchedule(1);
        Slot s1 = makeSlotWithBucketingOrder(1, LocalDateTime.of(2026, 8, 1, 14, 0), order);
        Slot s2 = makeSlotWithBucketingOrder(1, LocalDateTime.of(2026, 8, 1, 20, 0), order);
        Slot s3 = makeSlotWithBucketingOrder(1, LocalDateTime.of(2026, 8, 2, 8, 0), order);
        Slot s4 = makeSlotWithBucketingOrder(1, LocalDateTime.of(2026, 8, 2, 14, 0), order);
        Slot s5 = makeSlotWithBucketingOrder(1, LocalDateTime.of(2026, 8, 2, 20, 0), order);

        List<org.openmrs.module.ipd.web.model.StageScheduleStatus> result =
                slotTimeCreationService.buildStageSchedules(Arrays.asList(s1, s2, s3, s4, s5));

        assertNotNull("Result should not be null", result);
        assertFalse("Result should not be empty", result.isEmpty());
        assertNotNull("Non-uniform days should have firstDay slots", result.get(0).getFirstDaySlotsStartTime());
        assertEquals("FirstDay should have 2 slots from day 1", 2, result.get(0).getFirstDaySlotsStartTime().size());
        assertNotNull("Non-uniform days should have remaining slots", result.get(0).getRemainingDaySlotsStartTime());
        assertEquals("Remaining should have 3 slots from day 2", 3, result.get(0).getRemainingDaySlotsStartTime().size());
        assertNull("Should not have dayWise slots when pattern is non-uniform", result.get(0).getDayWiseSlotsStartTime());
    }

    @Test
    public void shouldBucketMultipleDays_WithDayWiseMiddlePattern() throws Exception {
        DrugOrder order = createDrugOrderWithFixedSchedule(1);
        Slot s1 = makeSlotWithBucketingOrder(1, LocalDateTime.of(2026, 8, 1, 14, 0), order);
        Slot s2 = makeSlotWithBucketingOrder(1, LocalDateTime.of(2026, 8, 1, 20, 0), order);
        Slot s3 = makeSlotWithBucketingOrder(1, LocalDateTime.of(2026, 8, 2, 8, 0), order);
        Slot s4 = makeSlotWithBucketingOrder(1, LocalDateTime.of(2026, 8, 2, 14, 0), order);
        Slot s5 = makeSlotWithBucketingOrder(1, LocalDateTime.of(2026, 8, 2, 20, 0), order);
        Slot s6 = makeSlotWithBucketingOrder(1, LocalDateTime.of(2026, 8, 3, 8, 0), order);
        Slot s7 = makeSlotWithBucketingOrder(1, LocalDateTime.of(2026, 8, 3, 20, 0), order);

        List<org.openmrs.module.ipd.web.model.StageScheduleStatus> result =
                slotTimeCreationService.buildStageSchedules(Arrays.asList(s1, s2, s3, s4, s5, s6, s7));

        assertNotNull("Result should not be null", result);
        assertFalse("Result should not be empty", result.isEmpty());
        assertNotNull("Multi-day pattern should have firstDay slots", result.get(0).getFirstDaySlotsStartTime());
        assertEquals("FirstDay should have 2 slots from day 1", 2, result.get(0).getFirstDaySlotsStartTime().size());
        assertNotNull("Multi-day pattern should have dayWise slots (middle repeating day)", result.get(0).getDayWiseSlotsStartTime());
        assertEquals("DayWise should have 3 slots from day 2", 3, result.get(0).getDayWiseSlotsStartTime().size());
        assertNotNull("Multi-day pattern should have remaining slots", result.get(0).getRemainingDaySlotsStartTime());
        assertEquals("Remaining should have 2 slots from day 3", 2, result.get(0).getRemainingDaySlotsStartTime().size());
    }

    private double invokeGetFrequencyPerDayFromFhir(DrugOrder order, int sequence) throws Exception {
        Method method = SlotTimeCreationService.class.getDeclaredMethod("getFrequencyPerDayFromFhir", DrugOrder.class, Integer.class);
        method.setAccessible(true);
        return (double) method.invoke(slotTimeCreationService, order, sequence);
    }

    private double invokeNormalizeFhirDurationToDays(double duration, String durationUnit) throws Exception {
        Method method = SlotTimeCreationService.class.getDeclaredMethod("normalizeFhirDurationToDays", double.class, String.class);
        method.setAccessible(true);
        return (double) method.invoke(slotTimeCreationService, duration, durationUnit);
    }

    private int invokeComputeVdpNumberOfSlots(DrugOrder order, int sequence) throws Exception {
        Method method = SlotTimeCreationService.class.getDeclaredMethod("computeVdpNumberOfSlots", DrugOrder.class, Integer.class);
        method.setAccessible(true);
        return (int) method.invoke(slotTimeCreationService, order, sequence);
    }

    private DrugOrder createVdpDrugOrder(int sequence, String frequencyName, double durationDays, String durationUnit) {
        DrugOrder order = org.mockito.Mockito.mock(DrugOrder.class, org.mockito.Mockito.withSettings().lenient());
        String dosingInstructions = String.format(
                "[{\"sequence\":%d,\"timing\":{\"repeat\":{\"duration\":%.1f,\"durationUnit\":\"%s\"},\"code\":{\"text\":\"%s\"}}}]",
                sequence, durationDays, durationUnit, frequencyName
        );
        when(order.getDosingInstructions()).thenReturn(dosingInstructions);
        when(order.getUuid()).thenReturn("vdp-uuid-" + sequence);
        when(order.getFrequency()).thenReturn(null);
        return order;
    }

    @Test
    public void shouldNormalizeDaysDuration() throws Exception {
        assertEquals(1.0, invokeNormalizeFhirDurationToDays(1.0, "d"), 0.001);
    }

    @Test
    public void shouldNormalizeWeeksDuration() throws Exception {
        assertEquals(7.0, invokeNormalizeFhirDurationToDays(1.0, "wk"), 0.001);
    }

    @Test
    public void shouldNormalizeMonthsDuration() throws Exception {
        assertEquals(30.0, invokeNormalizeFhirDurationToDays(1.0, "mo"), 0.001);
    }

    @Test
    public void shouldNormalizeNullDurationUnitAsDays() throws Exception {
        assertEquals(2.0, invokeNormalizeFhirDurationToDays(2.0, null), 0.001);
    }

    @Test
    public void shouldComputeCorrectSlotCountForVdpOrder() throws Exception {
        DrugOrder order = createVdpDrugOrder(1, "Every 2 hours", 1.0, "d");
        int slots = invokeComputeVdpNumberOfSlots(order, 1);
        assertTrue("Should compute at least 1 slot", slots >= 1);
    }

    @Test
    public void shouldReturnOneSlotForVdpWithSingleCount() throws Exception {
        DrugOrder order = org.mockito.Mockito.mock(DrugOrder.class, org.mockito.Mockito.withSettings().lenient());
        String dosingInstructions = "[{\"sequence\":1,\"timing\":{\"repeat\":{\"count\":1},\"code\":{\"text\":\"STAT (Immediately)\"}}}]";
        when(order.getDosingInstructions()).thenReturn(dosingInstructions);
        when(order.getUuid()).thenReturn("vdp-single-uuid");
        when(order.getFrequency()).thenReturn(null);
        int slots = invokeComputeVdpNumberOfSlots(order, 1);
        assertEquals("count=1 should return exactly 1 slot", 1, slots);
    }

    @Test
    public void shouldReturnOneSlotWhenVdpSequenceNotFound() throws Exception {
        DrugOrder order = createVdpDrugOrder(1, "Every 2 hours", 1.0, "d");
        int slots = invokeComputeVdpNumberOfSlots(order, 99);
        assertEquals("Non-matching sequence should fallback to 1", 1, slots);
    }

    @Test
    public void shouldReturnOneSlotWhenDosingInstructionsInvalid() throws Exception {
        DrugOrder order = org.mockito.Mockito.mock(DrugOrder.class, org.mockito.Mockito.withSettings().lenient());
        when(order.getDosingInstructions()).thenReturn("invalid-json");
        when(order.getUuid()).thenReturn("vdp-invalid-uuid");
        when(order.getFrequency()).thenReturn(null);
        int slots = invokeComputeVdpNumberOfSlots(order, 1);
        assertEquals("Invalid JSON should fallback to 1", 1, slots);
    }

    @Test
    public void shouldCreateSlotsForVdpStartTimeDurationRequest() {
        DrugOrder order = createVdpDrugOrder(1, "Every 2 hours", 1.0, "d");
        LocalDateTime startTime = LocalDateTime.of(2026, 8, 20, 8, 0);
        long startEpoch = startTime.toEpochSecond(ZoneOffset.UTC) * 1000L;

        ScheduleMedicationRequest request = ScheduleMedicationRequest.builder()
                .medicationFrequency(ScheduleMedicationRequest.MedicationFrequency.START_TIME_DURATION_FREQUENCY)
                .slotStartTime(startEpoch)
                .variableDosageSequence(1)
                .build();

        SlotTimeCreationResult result = slotTimeCreationService.createSlotsStartTimeFrom(request, order);

        assertNotNull(result);
        assertTrue("Should create at least 1 slot", result.getSlotsStartTime().size() >= 1);
    }

    @Test
    public void shouldCreateSingleSlotForVdpWithCountOne() {
        DrugOrder order = org.mockito.Mockito.mock(DrugOrder.class, org.mockito.Mockito.withSettings().lenient());
        String dosingInstructions = "[{\"sequence\":1,\"timing\":{\"repeat\":{\"count\":1},\"code\":{\"text\":\"STAT\"}}}]";
        when(order.getDosingInstructions()).thenReturn(dosingInstructions);
        when(order.getUuid()).thenReturn("vdp-stat-uuid");
        when(order.getFrequency()).thenReturn(null);

        LocalDateTime startTime = LocalDateTime.of(2026, 8, 20, 8, 0);
        long startEpoch = startTime.toEpochSecond(ZoneOffset.UTC) * 1000L;

        ScheduleMedicationRequest request = ScheduleMedicationRequest.builder()
                .medicationFrequency(ScheduleMedicationRequest.MedicationFrequency.START_TIME_DURATION_FREQUENCY)
                .slotStartTime(startEpoch)
                .variableDosageSequence(1)
                .build();

        SlotTimeCreationResult result = slotTimeCreationService.createSlotsStartTimeFrom(request, order);

        assertNotNull(result);
        assertEquals("count=1 should create exactly 1 slot", 1, result.getSlotsStartTime().size());
    }
}
