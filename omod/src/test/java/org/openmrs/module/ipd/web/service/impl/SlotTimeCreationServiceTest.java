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

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.*;
import static org.mockito.Mockito.mock;

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

    // -----------------------------------------------------------------------
    // Core API Tests - createSlotsStartTimeFrom with SlotTimeCreationResult
    // -----------------------------------------------------------------------

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

    // -----------------------------------------------------------------------
    // Crossing Slots Tests - midnight boundary handling
    // -----------------------------------------------------------------------

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

    // -----------------------------------------------------------------------
    // Edge Cases and Null Safety Tests
    // -----------------------------------------------------------------------

    @Test
    public void shouldHandleNullScheduleInReadPersistedCrossings() {
        List<Slot> slots = new ArrayList<>();
        Slot slot = new Slot();
        slot.setSchedule(null);
        slots.add(slot);

        // This should not throw NPE - tested via buildStageSchedules which calls readPersistedCrossingSlots internally
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

    // -----------------------------------------------------------------------
    // Bucketing Tests - routes slots into firstDay, dayWise, remaining buckets
    // -----------------------------------------------------------------------

    @Test
    public void shouldBucketUniformDays_AsDayWise() {
        // 2 days x 2 slots each (no partial first day, no crossing slots)
        List<Slot> slots = new ArrayList<>();
        Slot s1 = new Slot();
        s1.setStartDateTime(LocalDateTime.of(2026, 8, 1, 8, 0));
        s1.setSchedule(new Schedule());
        slots.add(s1);

        Slot s2 = new Slot();
        s2.setStartDateTime(LocalDateTime.of(2026, 8, 1, 20, 0));
        s2.setSchedule(new Schedule());
        slots.add(s2);

        Slot s3 = new Slot();
        s3.setStartDateTime(LocalDateTime.of(2026, 8, 2, 8, 0));
        s3.setSchedule(new Schedule());
        slots.add(s3);

        Slot s4 = new Slot();
        s4.setStartDateTime(LocalDateTime.of(2026, 8, 2, 20, 0));
        s4.setSchedule(new Schedule());
        slots.add(s4);

        List<org.openmrs.module.ipd.web.model.StageScheduleStatus> result = slotTimeCreationService.buildStageSchedules(slots);

        assertNotNull("Result should not be null", result);
        assertTrue("Should have day-wise slots", result.get(0).getDayWiseSlotsStartTime() != null && !result.get(0).getDayWiseSlotsStartTime().isEmpty());
        assertNull("Should not have first-day slots for uniform days", result.get(0).getFirstDaySlotsStartTime());
    }

    @Test
    public void shouldBucketNonUniformFirstDay_WithRemainingDay() {
        // Day 1: 2 slots, Day 2: 3 slots (non-uniform)
        List<Slot> slots = new ArrayList<>();
        Slot s1 = new Slot();
        s1.setStartDateTime(LocalDateTime.of(2026, 8, 1, 14, 0));
        s1.setSchedule(new Schedule());
        slots.add(s1);

        Slot s2 = new Slot();
        s2.setStartDateTime(LocalDateTime.of(2026, 8, 1, 20, 0));
        s2.setSchedule(new Schedule());
        slots.add(s2);

        Slot s3 = new Slot();
        s3.setStartDateTime(LocalDateTime.of(2026, 8, 2, 8, 0));
        s3.setSchedule(new Schedule());
        slots.add(s3);

        Slot s4 = new Slot();
        s4.setStartDateTime(LocalDateTime.of(2026, 8, 2, 14, 0));
        s4.setSchedule(new Schedule());
        slots.add(s4);

        Slot s5 = new Slot();
        s5.setStartDateTime(LocalDateTime.of(2026, 8, 2, 20, 0));
        s5.setSchedule(new Schedule());
        slots.add(s5);

        List<org.openmrs.module.ipd.web.model.StageScheduleStatus> result = slotTimeCreationService.buildStageSchedules(slots);

        assertNotNull("Result should not be null", result);
        assertTrue("Should have first-day slots for partial first day", result.get(0).getFirstDaySlotsStartTime() != null && !result.get(0).getFirstDaySlotsStartTime().isEmpty());
        assertTrue("Should have remaining-day slots", result.get(0).getRemainingDaySlotsStartTime() != null && !result.get(0).getRemainingDaySlotsStartTime().isEmpty());
        assertNull("Should not have day-wise slots when first day is non-uniform", result.get(0).getDayWiseSlotsStartTime());
    }

    @Test
    public void shouldBucketMultipleDaysWithMixedPatterns() {
        // Day 1: 2 slots, Day 2: 3 slots, Day 3: 2 slots (3+ days with mixed patterns)
        List<Slot> slots = new ArrayList<>();

        Slot s1 = new Slot();
        s1.setStartDateTime(LocalDateTime.of(2026, 8, 1, 14, 0));
        s1.setSchedule(new Schedule());
        slots.add(s1);

        Slot s2 = new Slot();
        s2.setStartDateTime(LocalDateTime.of(2026, 8, 1, 20, 0));
        s2.setSchedule(new Schedule());
        slots.add(s2);

        Slot s3 = new Slot();
        s3.setStartDateTime(LocalDateTime.of(2026, 8, 2, 8, 0));
        s3.setSchedule(new Schedule());
        slots.add(s3);

        Slot s4 = new Slot();
        s4.setStartDateTime(LocalDateTime.of(2026, 8, 2, 14, 0));
        s4.setSchedule(new Schedule());
        slots.add(s4);

        Slot s5 = new Slot();
        s5.setStartDateTime(LocalDateTime.of(2026, 8, 2, 20, 0));
        s5.setSchedule(new Schedule());
        slots.add(s5);

        Slot s6 = new Slot();
        s6.setStartDateTime(LocalDateTime.of(2026, 8, 3, 8, 0));
        s6.setSchedule(new Schedule());
        slots.add(s6);

        Slot s7 = new Slot();
        s7.setStartDateTime(LocalDateTime.of(2026, 8, 3, 20, 0));
        s7.setSchedule(new Schedule());
        slots.add(s7);

        List<org.openmrs.module.ipd.web.model.StageScheduleStatus> result = slotTimeCreationService.buildStageSchedules(slots);

        assertNotNull("Result should not be null", result);
        assertTrue("Should have first-day slots", result.get(0).getFirstDaySlotsStartTime() != null && !result.get(0).getFirstDaySlotsStartTime().isEmpty());
        assertTrue("Should have day-wise slots for repeated middle pattern", result.get(0).getDayWiseSlotsStartTime() != null && !result.get(0).getDayWiseSlotsStartTime().isEmpty());
        assertTrue("Should have remaining-day slots for non-uniform final day", result.get(0).getRemainingDaySlotsStartTime() != null && !result.get(0).getRemainingDaySlotsStartTime().isEmpty());
    }
}
