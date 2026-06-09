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

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import static org.junit.Assert.assertEquals;

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

    /**
     * Builds a FIXED_SCHEDULE_FREQUENCY request with the given numberOfSlots and
     * a dayWiseSlotsStartTime list of the requested size (all pointing to future times).
     */
    private ScheduleMedicationRequest buildFixedRequest(Integer numberOfSlots, List<Long> dayWise) {
        return ScheduleMedicationRequest.builder()
                .medicationFrequency(ScheduleMedicationRequest.MedicationFrequency.FIXED_SCHEDULE_FREQUENCY)
                .numberOfSlots(numberOfSlots)
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

    // -----------------------------------------------------------------------
    // Fixed-schedule frequency tests
    // -----------------------------------------------------------------------

    @Test
    public void shouldUseNumberOfSlots_WhenSetInRequest_FixedSchedule() {
        // quantity=6, dose=2 → normal fallback would be 3 slots; numberOfSlots overrides to 2
        DrugOrder order = buildDrugOrder(6.0, 2.0);
        ScheduleMedicationRequest request = buildFixedRequest(2, futureEpochList(6));

        List<LocalDateTime> result = slotTimeCreationService.createSlotsStartTimeFrom(request, order);

        assertEquals(2, result.size());
    }

    @Test
    public void shouldFallBackToQuantityDivDose_WhenNumberOfSlotsIsNull_FixedSchedule() {
        // quantity=6, dose=2 → ceil(6/2) = 3 slots
        DrugOrder order = buildDrugOrder(6.0, 2.0);
        ScheduleMedicationRequest request = buildFixedRequest(null, futureEpochList(6));

        List<LocalDateTime> result = slotTimeCreationService.createSlotsStartTimeFrom(request, order);

        assertEquals(3, result.size());
    }

    @Test
    public void shouldUseNumberOfSlots_WhenSetToOne_OverridesQuantityDivDoseResult() {
        // numberOfSlots=1 should cap the result even though quantity/dose gives 3
        DrugOrder order = buildDrugOrder(6.0, 2.0);
        ScheduleMedicationRequest request = buildFixedRequest(1, futureEpochList(6));

        List<LocalDateTime> result = slotTimeCreationService.createSlotsStartTimeFrom(request, order);

        assertEquals(1, result.size());
    }

    @Test
    public void shouldReturnEmptyList_WhenNoTimeListsProvided() {
        DrugOrder order = buildDrugOrder(6.0, 2.0);
        ScheduleMedicationRequest request = ScheduleMedicationRequest.builder()
                .medicationFrequency(ScheduleMedicationRequest.MedicationFrequency.FIXED_SCHEDULE_FREQUENCY)
                .numberOfSlots(3)
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
}
