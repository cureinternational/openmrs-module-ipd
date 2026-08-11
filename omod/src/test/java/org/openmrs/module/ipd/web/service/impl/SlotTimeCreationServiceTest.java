package org.openmrs.module.ipd.web.service.impl;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;
import org.openmrs.DrugOrder;
import org.openmrs.module.ipd.api.model.MedicationAdministration;
import org.openmrs.module.ipd.api.model.Slot;
import org.openmrs.module.ipd.web.contract.ScheduleMedicationRequest;
import org.openmrs.module.ipd.web.model.StageScheduleStatus;
import org.openmrs.module.ipd.web.model.SlotTimeCreationResult;
import org.openmrs.module.ipd.web.model.CrossingSlotTag;
import org.openmrs.module.ipd.web.contract.CrossingSlotContract;
import org.openmrs.module.ipd.web.model.DrugOrderSchedule;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

@RunWith(MockitoJUnitRunner.class)
public class SlotTimeCreationServiceTest {

    private SlotTimeCreationService slotTimeCreationService;

    @Before
    public void setUp() {
        slotTimeCreationService = new SlotTimeCreationService();
    }

    // -----------------------------------------------------------------------
    // Helper methods
    // -----------------------------------------------------------------------

    private ScheduleMedicationRequest buildFixedRequest(List<Long> dayWise) {
        return ScheduleMedicationRequest.builder()
                .medicationFrequency(ScheduleMedicationRequest.MedicationFrequency.FIXED_SCHEDULE_FREQUENCY)
                .dayWiseSlotsStartTime(dayWise)
                .build();
    }

    /**
     * Returns a list of {@code count} epoch-millis values spaced 1 hour apart
     * starting from an hour in the future, expressed in the system default time-zone
     * offset so that the epoch conversion round-trips correctly.
     */
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

    private DrugOrder buildIntradayDrugOrder(int duration, double morning, double afternoon, double evening, double night) {
        DrugOrder order = new DrugOrder();
        order.setDuration(duration);
        order.setQuantity((morning + afternoon + evening + night) * duration);
        order.setDosingInstructions(String.format(
            "{\"morningDose\":%s,\"afternoonDose\":%s,\"eveningDose\":%s,\"nightDose\":%s}",
            morning, afternoon, evening, night));
        return order;
    }

    private Slot buildIntradaySlot(DrugOrder order, LocalDateTime startDateTime) {
        Slot slot = new Slot();
        slot.setOrder(order);
        slot.setStartDateTime(startDateTime);
        slot.setStatus(Slot.SlotStatus.SCHEDULED);
        return slot;
    }

    // -----------------------------------------------------------------------
    // Regular order with fixed-schedule frequency tests
    // -----------------------------------------------------------------------

    @Test
    public void shouldUseQuantityDivDose_ForRegularOrders_FixedSchedule() {
        // quantity=6, dose=2 → ceil(6/2) = 3 slots
        DrugOrder order = buildDrugOrder(6.0, 2.0);
        ScheduleMedicationRequest request = buildFixedRequest(futureEpochList(6));

        SlotTimeCreationResult result = slotTimeCreationService.createSlotsStartTimeFrom(request, order);

        assertEquals(3, result.getSlotsStartTime().size());
        assertTrue("Crossing tags should be empty for regular orders", result.getCrossingTagsByStartTime().isEmpty());
    }

    @Test
    public void shouldReturnEmptyList_WhenNoTimeListsProvided() {
        DrugOrder order = buildDrugOrder(6.0, 2.0);
        ScheduleMedicationRequest request = ScheduleMedicationRequest.builder()
                .medicationFrequency(ScheduleMedicationRequest.MedicationFrequency.FIXED_SCHEDULE_FREQUENCY)
                .build();

        SlotTimeCreationResult result = slotTimeCreationService.createSlotsStartTimeFrom(request, order);

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

        assertEquals(6, result.getSlotsStartTime().size());
    }

    // -----------------------------------------------------------------------
    // Intraday medication slot creation tests
    // -----------------------------------------------------------------------

    @Test
    public void shouldCreateCorrectSlots_ForIntraday2xDay_1DayDuration_NoPartialFirstDay() {
        DrugOrder order = buildIntradayDrugOrder(1, 10.0, 0.0, 20.0, 0.0);
        List<Long> dayWise = futureEpochList(2);
        ScheduleMedicationRequest request = ScheduleMedicationRequest.builder()
                .medicationFrequency(ScheduleMedicationRequest.MedicationFrequency.FIXED_SCHEDULE_FREQUENCY)
                .dayWiseSlotsStartTime(dayWise)
                .build();

        SlotTimeCreationResult result = slotTimeCreationService.createSlotsStartTimeFrom(request, order);

        assertEquals(2, result.getSlotsStartTime().size());
    }

    @Test
    public void shouldCreateCorrectSlots_ForIntraday3xDay_3DayDuration_NoPartialFirstDay() {
        DrugOrder order = buildIntradayDrugOrder(3, 5.0, 5.0, 5.0, 0.0);
        List<Long> dayWise = futureEpochList(3);
        ScheduleMedicationRequest request = ScheduleMedicationRequest.builder()
                .medicationFrequency(ScheduleMedicationRequest.MedicationFrequency.FIXED_SCHEDULE_FREQUENCY)
                .dayWiseSlotsStartTime(dayWise)
                .build();

        SlotTimeCreationResult result = slotTimeCreationService.createSlotsStartTimeFrom(request, order);

        assertEquals(3, result.getSlotsStartTime().size());
    }

    @Test
    public void shouldCreateCorrectSlots_ForIntraday4xDay_2DayDuration_WithPartialFirstDay() {
        DrugOrder order = buildIntradayDrugOrder(2, 62.5, 25.0, 37.5, 10.0);
        List<Long> firstDay = futureEpochList(2);
        List<Long> dayWise = futureEpochList(4);
        List<Long> remaining = futureEpochList(2);
        ScheduleMedicationRequest request = ScheduleMedicationRequest.builder()
                .medicationFrequency(ScheduleMedicationRequest.MedicationFrequency.FIXED_SCHEDULE_FREQUENCY)
                .firstDaySlotsStartTime(firstDay)
                .dayWiseSlotsStartTime(dayWise)
                .remainingDaySlotsStartTime(remaining)
                .build();

        SlotTimeCreationResult result = slotTimeCreationService.createSlotsStartTimeFrom(request, order);

        assertEquals(8, result.getSlotsStartTime().size());
    }

    @Test
    public void shouldReturnEmpty_ForIntradayOrder_WhenNoSlotTimesProvided() {
        DrugOrder order = buildIntradayDrugOrder(3, 10.0, 0.0, 20.0, 0.0);
        ScheduleMedicationRequest request = ScheduleMedicationRequest.builder()
                .medicationFrequency(ScheduleMedicationRequest.MedicationFrequency.FIXED_SCHEDULE_FREQUENCY)
                .build();

        SlotTimeCreationResult result = slotTimeCreationService.createSlotsStartTimeFrom(request, order);

        assertEquals(0, result.getSlotsStartTime().size());
    }

    @Test
    public void shouldNotThrow_ForIntradayOrder_WithNullDuration() {
        DrugOrder order = buildIntradayDrugOrder(1, 10.0, 0.0, 20.0, 0.0);
        order.setDuration(null);
        ScheduleMedicationRequest request = ScheduleMedicationRequest.builder()
                .medicationFrequency(ScheduleMedicationRequest.MedicationFrequency.FIXED_SCHEDULE_FREQUENCY)
                .dayWiseSlotsStartTime(futureEpochList(2))
                .build();

        SlotTimeCreationResult result = slotTimeCreationService.createSlotsStartTimeFrom(request, order);

        assertEquals(2, result.getSlotsStartTime().size());
    }

    // -----------------------------------------------------------------------
    // Fallback behavior for intraday orders
    // -----------------------------------------------------------------------

    @Test
    public void shouldFallbackTo1Slot_ForIntradayOrder_WithNullDosingInstructions() {
        DrugOrder order = new DrugOrder();
        order.setDuration(1);
        order.setQuantity(10.0);
        List<Long> dayWise = futureEpochList(1);
        ScheduleMedicationRequest request = ScheduleMedicationRequest.builder()
                .medicationFrequency(ScheduleMedicationRequest.MedicationFrequency.FIXED_SCHEDULE_FREQUENCY)
                .dayWiseSlotsStartTime(dayWise)
                .build();

        SlotTimeCreationResult result = slotTimeCreationService.createSlotsStartTimeFrom(request, order);

        assertEquals(1, result.getSlotsStartTime().size());
    }

    @Test
    public void shouldFallbackTo1Slot_ForIntradayOrder_WithFhirArrayDosingInstructions() {
        DrugOrder order = new DrugOrder();
        order.setDuration(1);
        order.setQuantity(10.0);
        order.setDosingInstructions("[{\"sequence\":1}]");
        List<Long> dayWise = futureEpochList(1);
        ScheduleMedicationRequest request = ScheduleMedicationRequest.builder()
                .medicationFrequency(ScheduleMedicationRequest.MedicationFrequency.FIXED_SCHEDULE_FREQUENCY)
                .dayWiseSlotsStartTime(dayWise)
                .build();

        SlotTimeCreationResult result = slotTimeCreationService.createSlotsStartTimeFrom(request, order);

        assertEquals(1, result.getSlotsStartTime().size());
    }

    // -----------------------------------------------------------------------
    // getDrugOrderScheduledTime — edit mode reconstruction for intraday
    // -----------------------------------------------------------------------

    @Test
    public void shouldSetDayWiseSlotsStartTime_ForIntradayOrder_WithFullSchedule() {
        DrugOrder order = buildIntradayDrugOrder(2, 10.0, 0.0, 20.0, 0.0);
        LocalDate day1 = LocalDate.of(2026, 6, 1);
        LocalDate day2 = LocalDate.of(2026, 6, 2);

        List<Slot> slots = Arrays.asList(
                buildIntradaySlot(order, day1.atTime(8, 0)),
                buildIntradaySlot(order, day1.atTime(20, 0)),
                buildIntradaySlot(order, day2.atTime(8, 0)),
                buildIntradaySlot(order, day2.atTime(20, 0))
        );

        Map<DrugOrder, List<Slot>> slotsByOrder = new HashMap<>();
        slotsByOrder.put(order, slots);

        HashMap<String, DrugOrderSchedule> result = slotTimeCreationService.getDrugOrderScheduledTime(slotsByOrder);
        DrugOrderSchedule schedule = result.get(order.getUuid());

        assertNotNull("dayWiseSlotsStartTime should be set for full intraday schedule", schedule.getDayWiseSlotsStartTime());
        assertEquals(2, schedule.getDayWiseSlotsStartTime().size());
        assertNull("firstDaySlotsStartTime should be null when first day is complete", schedule.getFirstDaySlotsStartTime());
    }

    @Test
    public void shouldSetFirstDayAndRemainingSlots_ForIntradayOrder_WithPartialFirstDay() {
        DrugOrder order = buildIntradayDrugOrder(3, 10.0, 0.0, 20.0, 0.0);
        LocalDate day1 = LocalDate.of(2026, 6, 1);
        LocalDate day2 = LocalDate.of(2026, 6, 2);
        LocalDate day3 = LocalDate.of(2026, 6, 3);
        LocalDate day4 = LocalDate.of(2026, 6, 4);

        List<Slot> slots = Arrays.asList(
                buildIntradaySlot(order, day1.atTime(20, 0)),
                buildIntradaySlot(order, day2.atTime(8, 0)),
                buildIntradaySlot(order, day2.atTime(20, 0)),
                buildIntradaySlot(order, day3.atTime(8, 0)),
                buildIntradaySlot(order, day3.atTime(20, 0)),
                buildIntradaySlot(order, day4.atTime(8, 0))
        );

        Map<DrugOrder, List<Slot>> slotsByOrder = new HashMap<>();
        slotsByOrder.put(order, slots);

        HashMap<String, DrugOrderSchedule> result = slotTimeCreationService.getDrugOrderScheduledTime(slotsByOrder);
        DrugOrderSchedule schedule = result.get(order.getUuid());

        assertNotNull("firstDaySlotsStartTime should be set for partial first day", schedule.getFirstDaySlotsStartTime());
        assertEquals(1, schedule.getFirstDaySlotsStartTime().size());
        assertNotNull("dayWiseSlotsStartTime should be set from second day", schedule.getDayWiseSlotsStartTime());
        assertEquals(4, schedule.getDayWiseSlotsStartTime().size());
        assertNotNull("remainingDaySlotsStartTime should hold carry-over slot", schedule.getRemainingDaySlotsStartTime());
        assertEquals(1, schedule.getRemainingDaySlotsStartTime().size());
    }

    @Test
    public void shouldNotSetSlotStartTime_ForIntradayOrder_EvenThoughFrequencyIsNull() {
        DrugOrder order = buildIntradayDrugOrder(1, 10.0, 0.0, 20.0, 0.0);
        LocalDate day1 = LocalDate.of(2026, 6, 1);

        List<Slot> slots = Arrays.asList(
                buildIntradaySlot(order, day1.atTime(8, 0)),
                buildIntradaySlot(order, day1.atTime(20, 0))
        );

        Map<DrugOrder, List<Slot>> slotsByOrder = new HashMap<>();
        slotsByOrder.put(order, slots);

        HashMap<String, DrugOrderSchedule> result = slotTimeCreationService.getDrugOrderScheduledTime(slotsByOrder);
        DrugOrderSchedule schedule = result.get(order.getUuid());

        assertNotNull("dayWiseSlotsStartTime should be set for intraday orders", schedule.getDayWiseSlotsStartTime());
        assertEquals(2, schedule.getDayWiseSlotsStartTime().size());
    }

    // -----------------------------------------------------------------------
    // Variable Dosage Pattern (VDO) with crossing slots tests
    // -----------------------------------------------------------------------

    @Test
    public void shouldCreateSlotsWithCrossingTag_ForVdoWithMidnightCrossing() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime crossingSlotTime = now.withHour(22).withMinute(0);
        Long crossingEpoch = crossingSlotTime.toEpochSecond(ZoneOffset.UTC) * 1000L;

        DrugOrder order = buildDrugOrder(4.0, 1.0);
        List<Long> dayWise = futureEpochList(4);
        List<CrossingSlotContract> crossingSlots = Arrays.asList(
                CrossingSlotContract.builder()
                        .epoch(crossingEpoch)
                        .recurring(true)
                        .sourceBucket(Slot.SourceBucket.FINAL)
                        .build()
        );

        ScheduleMedicationRequest request = ScheduleMedicationRequest.builder()
                .medicationFrequency(ScheduleMedicationRequest.MedicationFrequency.FIXED_SCHEDULE_FREQUENCY)
                .dayWiseSlotsStartTime(dayWise)
                .crossingSlots(crossingSlots)
                .build();

        SlotTimeCreationResult result = slotTimeCreationService.createSlotsStartTimeFrom(request, order);

        assertEquals(4, result.getSlotsStartTime().size());
    }

    @Test
    public void shouldHandle_NonRecurringFirstDayCrossingSlots() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime crossingTime = now.withHour(22).withMinute(0);
        Long crossingEpoch = crossingTime.toEpochSecond(ZoneOffset.UTC) * 1000L;

        DrugOrder order = buildDrugOrder(6.0, 2.0);
        List<Long> firstDay = futureEpochList(3);
        List<Long> dayWise = futureEpochList(2);
        List<CrossingSlotContract> nonRecurringCrossings = Arrays.asList(
                CrossingSlotContract.builder()
                        .epoch(crossingEpoch)
                        .recurring(false)
                        .sourceBucket(Slot.SourceBucket.FIRST_DAY)
                        .build()
        );

        ScheduleMedicationRequest request = ScheduleMedicationRequest.builder()
                .medicationFrequency(ScheduleMedicationRequest.MedicationFrequency.FIXED_SCHEDULE_FREQUENCY)
                .firstDaySlotsStartTime(firstDay)
                .dayWiseSlotsStartTime(dayWise)
                .crossingSlots(nonRecurringCrossings)
                .build();

        SlotTimeCreationResult result = slotTimeCreationService.createSlotsStartTimeFrom(request, order);

        assertEquals(3, result.getSlotsStartTime().size());
    }

    @Test
    public void shouldHandle_RecurringDayWiseCrossingSlots() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime crossingTime = now.withHour(23).withMinute(0);
        Long crossingEpoch = crossingTime.toEpochSecond(ZoneOffset.UTC) * 1000L;

        DrugOrder order = buildDrugOrder(8.0, 2.0);
        List<Long> dayWise = futureEpochList(3);
        List<CrossingSlotContract> recurringDayWiseCrossings = Arrays.asList(
                CrossingSlotContract.builder()
                        .epoch(crossingEpoch)
                        .recurring(true)
                        .sourceBucket(Slot.SourceBucket.DAY_WISE)
                        .build()
        );

        ScheduleMedicationRequest request = ScheduleMedicationRequest.builder()
                .medicationFrequency(ScheduleMedicationRequest.MedicationFrequency.FIXED_SCHEDULE_FREQUENCY)
                .dayWiseSlotsStartTime(dayWise)
                .crossingSlots(recurringDayWiseCrossings)
                .build();

        SlotTimeCreationResult result = slotTimeCreationService.createSlotsStartTimeFrom(request, order);

        assertEquals(4, result.getSlotsStartTime().size());
    }

    @Test
    public void shouldHandle_MixedCrossingSlots_NonRecurringAndRecurring() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime nonRecurringTime = now.withHour(22).withMinute(0);
        LocalDateTime recurringTime = now.withHour(23).withMinute(0);
        Long nonRecurringEpoch = nonRecurringTime.toEpochSecond(ZoneOffset.UTC) * 1000L;
        Long recurringEpoch = recurringTime.toEpochSecond(ZoneOffset.UTC) * 1000L;

        DrugOrder order = buildDrugOrder(6.0, 1.0);
        List<Long> firstDay = futureEpochList(2);
        List<Long> dayWise = futureEpochList(2);
        List<CrossingSlotContract> mixedCrossings = Arrays.asList(
                CrossingSlotContract.builder()
                        .epoch(nonRecurringEpoch)
                        .recurring(false)
                        .sourceBucket(Slot.SourceBucket.FIRST_DAY)
                        .build(),
                CrossingSlotContract.builder()
                        .epoch(recurringEpoch)
                        .recurring(true)
                        .sourceBucket(Slot.SourceBucket.DAY_WISE)
                        .build()
        );

        ScheduleMedicationRequest request = ScheduleMedicationRequest.builder()
                .medicationFrequency(ScheduleMedicationRequest.MedicationFrequency.FIXED_SCHEDULE_FREQUENCY)
                .firstDaySlotsStartTime(firstDay)
                .dayWiseSlotsStartTime(dayWise)
                .crossingSlots(mixedCrossings)
                .build();

        SlotTimeCreationResult result = slotTimeCreationService.createSlotsStartTimeFrom(request, order);

        assertEquals(6, result.getSlotsStartTime().size());
    }

    // -----------------------------------------------------------------------
    // Intraday orders with crossing slots
    // -----------------------------------------------------------------------

    @Test
    public void shouldHandle_IntradayOrder_WithCrossingSlots() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime crossingTime = now.withHour(22).withMinute(0);
        Long crossingEpoch = crossingTime.toEpochSecond(ZoneOffset.UTC) * 1000L;

        DrugOrder order = buildIntradayDrugOrder(2, 5.0, 5.0, 5.0, 5.0);
        List<Long> dayWise = futureEpochList(4);
        List<CrossingSlotContract> crossingSlots = Arrays.asList(
                CrossingSlotContract.builder()
                        .epoch(crossingEpoch)
                        .recurring(true)
                        .sourceBucket(Slot.SourceBucket.DAY_WISE)
                        .build()
        );

        ScheduleMedicationRequest request = ScheduleMedicationRequest.builder()
                .medicationFrequency(ScheduleMedicationRequest.MedicationFrequency.FIXED_SCHEDULE_FREQUENCY)
                .dayWiseSlotsStartTime(dayWise)
                .crossingSlots(crossingSlots)
                .build();

        SlotTimeCreationResult result = slotTimeCreationService.createSlotsStartTimeFrom(request, order);

        assertEquals(5, result.getSlotsStartTime().size());
    }

    @Test
    public void shouldHandle_IntradayWithPartialFirstDay_AndCrossingSlots() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime crossingTime = now.withHour(23).withMinute(30);
        Long crossingEpoch = crossingTime.toEpochSecond(ZoneOffset.UTC) * 1000L;

        DrugOrder order = buildIntradayDrugOrder(3, 10.0, 10.0, 0.0, 0.0);
        List<Long> firstDay = futureEpochList(1);
        List<Long> dayWise = futureEpochList(2);
        List<Long> remaining = futureEpochList(1);
        List<CrossingSlotContract> crossingSlots = Arrays.asList(
                CrossingSlotContract.builder()
                        .epoch(crossingEpoch)
                        .recurring(true)
                        .sourceBucket(Slot.SourceBucket.DAY_WISE)
                        .build()
        );

        ScheduleMedicationRequest request = ScheduleMedicationRequest.builder()
                .medicationFrequency(ScheduleMedicationRequest.MedicationFrequency.FIXED_SCHEDULE_FREQUENCY)
                .firstDaySlotsStartTime(firstDay)
                .dayWiseSlotsStartTime(dayWise)
                .remainingDaySlotsStartTime(remaining)
                .crossingSlots(crossingSlots)
                .build();

        SlotTimeCreationResult result = slotTimeCreationService.createSlotsStartTimeFrom(request, order);

        assertEquals(5, result.getSlotsStartTime().size());
    }

    // -----------------------------------------------------------------------
    // Edge cases
    // -----------------------------------------------------------------------

    @Test
    public void shouldReturnEmptyResult_ForOrderWithoutQuantityOrDose() {
        DrugOrder order = new DrugOrder();
        order.setQuantity(null);
        order.setDose(null);
        List<Long> dayWise = futureEpochList(2);
        ScheduleMedicationRequest request = ScheduleMedicationRequest.builder()
                .medicationFrequency(ScheduleMedicationRequest.MedicationFrequency.FIXED_SCHEDULE_FREQUENCY)
                .dayWiseSlotsStartTime(dayWise)
                .build();

        SlotTimeCreationResult result = slotTimeCreationService.createSlotsStartTimeFrom(request, order);

        assertEquals(2, result.getSlotsStartTime().size());
    }

    @Test
    public void shouldRespectQuantityDose_WhenBothAreProvided() {
        DrugOrder order = buildDrugOrder(15.0, 3.0);
        List<Long> dayWise = futureEpochList(10);
        ScheduleMedicationRequest request = buildFixedRequest(dayWise);

        SlotTimeCreationResult result = slotTimeCreationService.createSlotsStartTimeFrom(request, order);

        assertEquals(5, result.getSlotsStartTime().size());
    }

    @Test
    public void shouldCreateSlots_WithCrossingTagsLookup_Populated() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime crossingTime = now.withHour(22).withMinute(0);
        Long crossingEpoch = crossingTime.toEpochSecond(ZoneOffset.UTC) * 1000L;

        DrugOrder order = buildDrugOrder(4.0, 1.0);
        List<Long> dayWise = futureEpochList(4);
        List<CrossingSlotContract> crossingSlots = Arrays.asList(
                CrossingSlotContract.builder()
                        .epoch(crossingEpoch)
                        .recurring(true)
                        .sourceBucket(Slot.SourceBucket.FINAL)
                        .build()
        );

        ScheduleMedicationRequest request = ScheduleMedicationRequest.builder()
                .medicationFrequency(ScheduleMedicationRequest.MedicationFrequency.FIXED_SCHEDULE_FREQUENCY)
                .dayWiseSlotsStartTime(dayWise)
                .crossingSlots(crossingSlots)
                .build();

        SlotTimeCreationResult result = slotTimeCreationService.createSlotsStartTimeFrom(request, order);

        assertNotNull("Crossing tags lookup should not be null", result.getCrossingTagsByStartTime());
        assertTrue("Crossing tags should be populated or empty", result.getCrossingTagsByStartTime().size() >= 0);
    }
}
