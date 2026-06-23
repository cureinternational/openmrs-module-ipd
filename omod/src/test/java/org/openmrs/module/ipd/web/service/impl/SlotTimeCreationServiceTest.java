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
    // Fixed-schedule frequency tests
    // -----------------------------------------------------------------------

    @Test
    public void shouldUseQuantityDivDose_ForRegularOrders_FixedSchedule() {
        // quantity=6, dose=2 → ceil(6/2) = 3 slots
        DrugOrder order = buildDrugOrder(6.0, 2.0);
        ScheduleMedicationRequest request = buildFixedRequest(futureEpochList(6));

        List<LocalDateTime> result = slotTimeCreationService.createSlotsStartTimeFrom(request, order);

        assertEquals(3, result.size());
    }

    @Test
    public void shouldReturnEmptyList_WhenNoTimeListsProvided() {
        DrugOrder order = buildDrugOrder(6.0, 2.0);
        ScheduleMedicationRequest request = ScheduleMedicationRequest.builder()
                .medicationFrequency(ScheduleMedicationRequest.MedicationFrequency.FIXED_SCHEDULE_FREQUENCY)
                .build();

        List<LocalDateTime> result = slotTimeCreationService.createSlotsStartTimeFrom(request, order);

        assertEquals(0, result.size());
    }

    // -----------------------------------------------------------------------
    // buildStageSchedules — fixed-schedule VDP branch
    // -----------------------------------------------------------------------

    private Slot makeVdpSlot(Integer sequence, Slot.SlotStatus status, LocalDateTime startDateTime, boolean hasAdmin) {
        Slot slot = new Slot();
        slot.setVariableDosageSequence(sequence);
        slot.setStatus(status);
        slot.setStartDateTime(startDateTime);
        slot.setNotes("note-" + sequence);
        if (hasAdmin) slot.setMedicationAdministration(new MedicationAdministration());
        org.openmrs.DrugOrder order = new org.openmrs.DrugOrder();
        order.setDosingInstructions("[{\"sequence\":" + sequence + ",\"timing\":{\"code\":{\"text\":\"Twice a day\"},\"repeat\":{\"duration\":2,\"durationUnit\":\"d\"}},\"doseAndRate\":[{\"doseQuantity\":{\"value\":5,\"unit\":\"mg\"}}],\"extension\":[{\"url\":\"isLoadingDose\",\"valueBoolean\":false}]}]");
        slot.setOrder(order);
        return slot;
    }

    @Test
    public void shouldProduceDayWiseSlotsStartTime_ForFixedScheduleVdpStageWithUniformDays() {
        // 2 days × 2 slots each = 4 total — uniform so dayWise should be populated
        LocalDateTime day1Slot1 = LocalDateTime.of(2026, 5, 1, 8, 0);
        LocalDateTime day1Slot2 = LocalDateTime.of(2026, 5, 1, 20, 0);
        LocalDateTime day2Slot1 = LocalDateTime.of(2026, 5, 2, 8, 0);
        LocalDateTime day2Slot2 = LocalDateTime.of(2026, 5, 2, 20, 0);

        Slot s1 = makeVdpSlot(2, Slot.SlotStatus.SCHEDULED, day1Slot1, false);
        Slot s2 = makeVdpSlot(2, Slot.SlotStatus.SCHEDULED, day1Slot2, false);
        Slot s3 = makeVdpSlot(2, Slot.SlotStatus.SCHEDULED, day2Slot1, false);
        Slot s4 = makeVdpSlot(2, Slot.SlotStatus.SCHEDULED, day2Slot2, false);

        List<StageScheduleStatus> result = slotTimeCreationService.buildStageSchedules(Arrays.asList(s1, s2, s3, s4));

        assertEquals(1, result.size());
        StageScheduleStatus stage = result.get(0);
        assertNull("Start-time field should be null for fixed-schedule stage", stage.getSlotStartTime());
        assertNotNull("dayWiseSlotsStartTime should be populated for uniform days", stage.getDayWiseSlotsStartTime());
        assertEquals(2, stage.getDayWiseSlotsStartTime().size());
        assertNull(stage.getFirstDaySlotsStartTime());
    }

    @Test
    public void shouldProduceFirstDayAndRemainingDay_ForFixedScheduleVdpStageWithNonUniformFirstDay() {
        // Day 1 has 1 slot (partial), day 2 has 2 slots — matches regular order behaviour:
        // firstDay=day1, remainingDay=day2, dayWise=null (same as getDrugOrderScheduledTime)
        LocalDateTime day1Slot1 = LocalDateTime.of(2026, 5, 1, 14, 0);
        LocalDateTime day2Slot1 = LocalDateTime.of(2026, 5, 2, 8, 0);
        LocalDateTime day2Slot2 = LocalDateTime.of(2026, 5, 2, 20, 0);

        Slot s1 = makeVdpSlot(2, Slot.SlotStatus.SCHEDULED, day1Slot1, false);
        Slot s2 = makeVdpSlot(2, Slot.SlotStatus.SCHEDULED, day2Slot1, false);
        Slot s3 = makeVdpSlot(2, Slot.SlotStatus.SCHEDULED, day2Slot2, false);

        List<StageScheduleStatus> result = slotTimeCreationService.buildStageSchedules(Arrays.asList(s1, s2, s3));

        assertEquals(1, result.size());
        StageScheduleStatus stage = result.get(0);
        assertNotNull("firstDaySlotsStartTime should be populated when day 1 is partial", stage.getFirstDaySlotsStartTime());
        assertEquals(1, stage.getFirstDaySlotsStartTime().size());
        assertNotNull("remainingDaySlotsStartTime should be populated from last day", stage.getRemainingDaySlotsStartTime());
        assertEquals(2, stage.getRemainingDaySlotsStartTime().size());
        assertNull("dayWiseSlotsStartTime should be null for exactly 2 calendar days", stage.getDayWiseSlotsStartTime());
    }

    @Test
    public void shouldProduceFirstDayDayWiseAndRemainingDay_ForThreePlusCalendarDays() {
        // Day 1: 1 slot (partial), Day 2: 2 slots (full pattern), Day 3: 1 slot (partial last day)
        LocalDateTime day1Slot1 = LocalDateTime.of(2026, 5, 1, 14, 0);
        LocalDateTime day2Slot1 = LocalDateTime.of(2026, 5, 2, 8, 0);
        LocalDateTime day2Slot2 = LocalDateTime.of(2026, 5, 2, 20, 0);
        LocalDateTime day3Slot1 = LocalDateTime.of(2026, 5, 3, 8, 0);

        Slot s1 = makeVdpSlot(2, Slot.SlotStatus.SCHEDULED, day1Slot1, false);
        Slot s2 = makeVdpSlot(2, Slot.SlotStatus.SCHEDULED, day2Slot1, false);
        Slot s3 = makeVdpSlot(2, Slot.SlotStatus.SCHEDULED, day2Slot2, false);
        Slot s4 = makeVdpSlot(2, Slot.SlotStatus.SCHEDULED, day3Slot1, false);

        List<StageScheduleStatus> result = slotTimeCreationService.buildStageSchedules(Arrays.asList(s1, s2, s3, s4));

        assertEquals(1, result.size());
        StageScheduleStatus stage = result.get(0);
        assertNotNull("firstDaySlotsStartTime should be populated for partial first day", stage.getFirstDaySlotsStartTime());
        assertEquals(1, stage.getFirstDaySlotsStartTime().size());
        assertNotNull("dayWiseSlotsStartTime should be populated from day 2 when 3+ days", stage.getDayWiseSlotsStartTime());
        assertEquals(2, stage.getDayWiseSlotsStartTime().size());
        assertNotNull("remainingDaySlotsStartTime should be populated from last day", stage.getRemainingDaySlotsStartTime());
        assertEquals(1, stage.getRemainingDaySlotsStartTime().size());
    }

    @Test
    public void shouldReturnEmptyList_WhenSlotsListIsNull() {
        List<StageScheduleStatus> result = slotTimeCreationService.buildStageSchedules(null);
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    public void shouldReturnEmptyList_WhenNoSlotsHaveVariableDosageSequence() {
        Slot slot = new Slot();
        slot.setVariableDosageSequence(null);
        slot.setStatus(Slot.SlotStatus.SCHEDULED);
        slot.setStartDateTime(LocalDateTime.now().plusHours(1));

        List<StageScheduleStatus> result = slotTimeCreationService.buildStageSchedules(Collections.singletonList(slot));
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    public void shouldSetIsScheduledTrue_ForAllStageEntries() {
        Slot slot = makeVdpSlot(1, Slot.SlotStatus.SCHEDULED, LocalDateTime.now().plusHours(1), false);
        // Override order with start-time frequency JSON
        org.openmrs.DrugOrder order = new org.openmrs.DrugOrder();
        order.setDosingInstructions("[{\"sequence\":1,\"timing\":{\"code\":{\"text\":\"Once a day\"}},\"extension\":[{\"url\":\"isLoadingDose\",\"valueBoolean\":false}]}]");
        slot.setOrder(order);

        List<StageScheduleStatus> result = slotTimeCreationService.buildStageSchedules(Collections.singletonList(slot));

        assertEquals(1, result.size());
        assertTrue("isScheduled should always be true for any entry in stageSchedules", result.get(0).getIsScheduled());
    }

    @Test
    public void shouldSetAdministrationStarted_WhenAnySlotHasAdministration() {
        Slot slot = makeVdpSlot(1, Slot.SlotStatus.COMPLETED, LocalDateTime.now().minusHours(1), true);
        org.openmrs.DrugOrder order = new org.openmrs.DrugOrder();
        order.setDosingInstructions("[{\"sequence\":1,\"timing\":{\"code\":{\"text\":\"Once\"}},\"extension\":[{\"url\":\"isLoadingDose\",\"valueBoolean\":true}]}]");
        slot.setOrder(order);

        List<StageScheduleStatus> result = slotTimeCreationService.buildStageSchedules(Collections.singletonList(slot));

        assertTrue(result.get(0).getAdministrationStarted());
    }

    @Test
    public void shouldNotSetAllAttended_WhenAnySlotIsStillScheduled() {
        Slot slot = makeVdpSlot(1, Slot.SlotStatus.SCHEDULED, LocalDateTime.now().plusHours(1), false);
        org.openmrs.DrugOrder order = new org.openmrs.DrugOrder();
        order.setDosingInstructions("[{\"sequence\":1,\"timing\":{\"code\":{\"text\":\"Once\"}},\"extension\":[{\"url\":\"isLoadingDose\",\"valueBoolean\":true}]}]");
        slot.setOrder(order);

        List<StageScheduleStatus> result = slotTimeCreationService.buildStageSchedules(Collections.singletonList(slot));

        assertFalse(result.get(0).getAllAttended());
    }

    // -----------------------------------------------------------------------
    // Intraday slot creation — createSlotsStartTimeFrom
    // -----------------------------------------------------------------------

    @Test
    public void shouldCreateCorrectSlots_ForIntraday2xDay_1DayDuration_NoPartialFirstDay() {
        DrugOrder order = buildIntradayDrugOrder(1, 10.0, 0.0, 20.0, 0.0);
        List<Long> dayWise = futureEpochList(2);
        ScheduleMedicationRequest request = ScheduleMedicationRequest.builder()
                .medicationFrequency(ScheduleMedicationRequest.MedicationFrequency.FIXED_SCHEDULE_FREQUENCY)
                .dayWiseSlotsStartTime(dayWise)
                .build();

        List<LocalDateTime> result = slotTimeCreationService.createSlotsStartTimeFrom(request, order);

        assertEquals(2, result.size());
    }

    @Test
    public void shouldCreateCorrectSlots_ForIntraday3xDay_3DayDuration_NoPartialFirstDay() {
        DrugOrder order = buildIntradayDrugOrder(3, 5.0, 5.0, 5.0, 0.0);
        List<Long> dayWise = futureEpochList(3);
        ScheduleMedicationRequest request = ScheduleMedicationRequest.builder()
                .medicationFrequency(ScheduleMedicationRequest.MedicationFrequency.FIXED_SCHEDULE_FREQUENCY)
                .dayWiseSlotsStartTime(dayWise)
                .build();

        List<LocalDateTime> result = slotTimeCreationService.createSlotsStartTimeFrom(request, order);

        assertEquals(9, result.size());
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

        List<LocalDateTime> result = slotTimeCreationService.createSlotsStartTimeFrom(request, order);

        assertEquals(8, result.size());
    }

    @Test
    public void shouldReturnEmpty_ForIntradayOrder_WhenNoSlotTimesProvided() {
        DrugOrder order = buildIntradayDrugOrder(3, 10.0, 0.0, 20.0, 0.0);
        ScheduleMedicationRequest request = ScheduleMedicationRequest.builder()
                .medicationFrequency(ScheduleMedicationRequest.MedicationFrequency.FIXED_SCHEDULE_FREQUENCY)
                .build();

        List<LocalDateTime> result = slotTimeCreationService.createSlotsStartTimeFrom(request, order);

        assertEquals(0, result.size());
    }

    // -----------------------------------------------------------------------
    // getIntradayFrequencyPerDay — fallback behaviour (via slot count)
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

        List<LocalDateTime> result = slotTimeCreationService.createSlotsStartTimeFrom(request, order);

        assertEquals(1, result.size());
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

        List<LocalDateTime> result = slotTimeCreationService.createSlotsStartTimeFrom(request, order);

        assertEquals(1, result.size());
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
        assertEquals(2, schedule.getDayWiseSlotsStartTime().size());
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

        assertNull("slotStartTime should NOT be set for intraday orders even though frequency is null",
                schedule.getSlotStartTime());
        assertNotNull("dayWiseSlotsStartTime should be set instead", schedule.getDayWiseSlotsStartTime());
    }
}
