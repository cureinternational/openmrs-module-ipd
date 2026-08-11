package org.openmrs.module.ipd.web.service.impl;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;
import org.openmrs.DrugOrder;
import org.openmrs.module.ipd.web.contract.ScheduleMedicationRequest;
import org.openmrs.module.ipd.web.model.SlotTimeCreationResult;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
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
}
