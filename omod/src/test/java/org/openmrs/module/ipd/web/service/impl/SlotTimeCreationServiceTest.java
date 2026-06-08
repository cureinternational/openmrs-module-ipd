package org.openmrs.module.ipd.web.service.impl;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;
import org.openmrs.DrugOrder;
import org.openmrs.module.ipd.web.contract.ScheduleMedicationRequest;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.List;

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
}
