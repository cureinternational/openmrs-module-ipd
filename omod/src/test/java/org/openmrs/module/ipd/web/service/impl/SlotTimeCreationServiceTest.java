package org.openmrs.module.ipd.web.service.impl;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;
import org.openmrs.DrugOrder;
import org.openmrs.module.ipd.api.model.Slot;
import org.openmrs.module.ipd.web.contract.ScheduleMedicationRequest;
import org.openmrs.module.ipd.web.model.SlotTimeCreationResult;
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

    // -----------------------------------------------------------------------
    // Regular order with fixed-schedule frequency tests
    // -----------------------------------------------------------------------

    @Test
    public void shouldUseQuantityDivDose_ForRegularOrders_FixedSchedule() {
        DrugOrder order = buildDrugOrder(6.0, 2.0);
        ScheduleMedicationRequest request = buildFixedRequest(futureEpochList(6));

        SlotTimeCreationResult result = slotTimeCreationService.createSlotsStartTimeFrom(request, order);

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

    @Test
    public void shouldRespectQuantityDose_WhenBothAreProvided() {
        DrugOrder order = buildDrugOrder(15.0, 3.0);
        List<Long> dayWise = futureEpochList(10);
        ScheduleMedicationRequest request = buildFixedRequest(dayWise);

        SlotTimeCreationResult result = slotTimeCreationService.createSlotsStartTimeFrom(request, order);

        assertEquals(5, result.getSlotsStartTime().size());
    }

    @Test
    public void shouldCreateSlots_WithCrossingTagsLookup_NotNull() {
        DrugOrder order = buildDrugOrder(4.0, 1.0);
        List<Long> dayWise = futureEpochList(4);
        ScheduleMedicationRequest request = buildFixedRequest(dayWise);

        SlotTimeCreationResult result = slotTimeCreationService.createSlotsStartTimeFrom(request, order);

        assertNotNull("Crossing tags lookup should not be null", result.getCrossingTagsByStartTime());
        assertEquals(4, result.getSlotsStartTime().size());
    }

    // -----------------------------------------------------------------------
    // Edit mode reconstruction for intraday (getDrugOrderScheduledTime)
    // -----------------------------------------------------------------------

    @Test
    public void shouldSetDayWiseSlotsStartTime_ForIntradayOrder_WithFullSchedule() {
        DrugOrder order = new DrugOrder();
        order.setDuration(2);
        order.setQuantity(40.0);
        LocalDate day1 = LocalDate.of(2026, 6, 1);
        LocalDate day2 = LocalDate.of(2026, 6, 2);

        List<Slot> slots = Arrays.asList(
                buildSlot(order, day1.atTime(8, 0)),
                buildSlot(order, day1.atTime(20, 0)),
                buildSlot(order, day2.atTime(8, 0)),
                buildSlot(order, day2.atTime(20, 0))
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
        DrugOrder order = new DrugOrder();
        order.setDuration(3);
        order.setQuantity(60.0);
        LocalDate day1 = LocalDate.of(2026, 6, 1);
        LocalDate day2 = LocalDate.of(2026, 6, 2);
        LocalDate day3 = LocalDate.of(2026, 6, 3);
        LocalDate day4 = LocalDate.of(2026, 6, 4);

        List<Slot> slots = Arrays.asList(
                buildSlot(order, day1.atTime(20, 0)),
                buildSlot(order, day2.atTime(8, 0)),
                buildSlot(order, day2.atTime(20, 0)),
                buildSlot(order, day3.atTime(8, 0)),
                buildSlot(order, day3.atTime(20, 0)),
                buildSlot(order, day4.atTime(8, 0))
        );

        Map<DrugOrder, List<Slot>> slotsByOrder = new HashMap<>();
        slotsByOrder.put(order, slots);

        HashMap<String, DrugOrderSchedule> result = slotTimeCreationService.getDrugOrderScheduledTime(slotsByOrder);
        DrugOrderSchedule schedule = result.get(order.getUuid());

        assertNotNull("firstDaySlotsStartTime should be set for partial first day", schedule.getFirstDaySlotsStartTime());
        assertEquals(1, schedule.getFirstDaySlotsStartTime().size());
        assertNotNull("dayWiseSlotsStartTime should be set from second day", schedule.getDayWiseSlotsStartTime());
        assertNotNull("remainingDaySlotsStartTime should hold carry-over slot", schedule.getRemainingDaySlotsStartTime());
    }

    private Slot buildSlot(DrugOrder order, LocalDateTime startDateTime) {
        Slot slot = new Slot();
        slot.setOrder(order);
        slot.setStartDateTime(startDateTime);
        slot.setStatus(Slot.SlotStatus.SCHEDULED);
        return slot;
    }
}
