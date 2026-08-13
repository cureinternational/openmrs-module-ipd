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
}
