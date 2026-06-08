package org.openmrs.module.ipd.web.contract;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;
import org.openmrs.module.ipd.api.model.MedicationAdministration;
import org.openmrs.module.ipd.api.model.Slot;
import org.openmrs.module.ipd.web.model.DrugOrderSchedule;
import org.openmrs.module.ipd.web.model.StageScheduleStatus;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

@RunWith(MockitoJUnitRunner.class)
public class DrugOrderScheduleResponseTest {

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private Slot makeSlot(Integer sequence, Slot.SlotStatus status, boolean hasAdministration) {
        Slot slot = new Slot();
        slot.setVariableDosageSequence(sequence);
        slot.setStatus(status);
        if (hasAdministration) {
            slot.setMedicationAdministration(new MedicationAdministration());
        }
        // Future start time so pendingSlotsAvailable logic works correctly for SCHEDULED slots
        slot.setStartDateTime(LocalDateTime.now().plusHours(1));
        slot.setNotes("note-" + sequence);
        return slot;
    }

    private DrugOrderSchedule makeSchedule(List<Slot> slots) {
        DrugOrderSchedule schedule = new DrugOrderSchedule();
        schedule.setSlots(slots);
        return schedule;
    }

    // -----------------------------------------------------------------------
    // stageSchedules grouping tests
    // -----------------------------------------------------------------------

    @Test
    public void shouldBuildStageSchedules_WhenSlotsHaveVariableDosageSequence() {
        Slot slot1 = makeSlot(1, Slot.SlotStatus.SCHEDULED, false);
        Slot slot2 = makeSlot(2, Slot.SlotStatus.SCHEDULED, false);

        DrugOrderScheduleResponse response = DrugOrderScheduleResponse.createFrom(makeSchedule(Arrays.asList(slot1, slot2)));

        List<StageScheduleStatus> stageSchedules = response.getStageSchedules();
        assertNotNull(stageSchedules);
        assertEquals(2, stageSchedules.size());

        // Every entry must have a non-null variableDosageSequence
        assertTrue(stageSchedules.stream().allMatch(s -> s.getVariableDosageSequence() != null));
        // The two distinct sequences (1 and 2) must both be present
        assertTrue(stageSchedules.stream().anyMatch(s -> s.getVariableDosageSequence() == 1));
        assertTrue(stageSchedules.stream().anyMatch(s -> s.getVariableDosageSequence() == 2));
    }

    @Test
    public void shouldNotBuildStageSchedules_WhenAllSlotsHaveNullSequence() {
        Slot slot1 = makeSlot(null, Slot.SlotStatus.SCHEDULED, false);
        Slot slot2 = makeSlot(null, Slot.SlotStatus.SCHEDULED, false);
        // Null notes would NPE on slots.get(0).getNotes() only if slot list is empty; provide one note
        slot1.setNotes("note");
        slot2.setNotes("note");

        DrugOrderScheduleResponse response = DrugOrderScheduleResponse.createFrom(makeSchedule(Arrays.asList(slot1, slot2)));

        assertNotNull(response.getStageSchedules());
        assertTrue(response.getStageSchedules().isEmpty());
    }

    // -----------------------------------------------------------------------
    // allAttended flag per stage
    // -----------------------------------------------------------------------

    @Test
    public void shouldSetAllAttended_WhenNoSlotsInStageAreScheduled() {
        Slot completedSlot = makeSlot(1, Slot.SlotStatus.COMPLETED, false);

        DrugOrderScheduleResponse response = DrugOrderScheduleResponse.createFrom(makeSchedule(Collections.singletonList(completedSlot)));

        StageScheduleStatus stage = response.getStageSchedules().get(0);
        assertTrue("Expected allAttended=true for a COMPLETED slot", stage.getAllAttended());
    }

    @Test
    public void shouldNotSetAllAttended_WhenSomeSlotInStageIsStillScheduled() {
        Slot scheduledSlot = makeSlot(1, Slot.SlotStatus.SCHEDULED, false);

        DrugOrderScheduleResponse response = DrugOrderScheduleResponse.createFrom(makeSchedule(Collections.singletonList(scheduledSlot)));

        StageScheduleStatus stage = response.getStageSchedules().get(0);
        assertFalse("Expected allAttended=false for a SCHEDULED slot", stage.getAllAttended());
    }

    @Test
    public void shouldSetAllAttended_WhenSlotIsNotDone() {
        Slot notDoneSlot = makeSlot(1, Slot.SlotStatus.NOT_DONE, false);

        DrugOrderScheduleResponse response = DrugOrderScheduleResponse.createFrom(makeSchedule(Collections.singletonList(notDoneSlot)));

        StageScheduleStatus stage = response.getStageSchedules().get(0);
        assertTrue("Expected allAttended=true for a NOT_DONE slot (skipped counts as attended)", stage.getAllAttended());
    }

    @Test
    public void shouldSetAllAttended_WhenSlotIsMissed() {
        Slot missedSlot = makeSlot(1, Slot.SlotStatus.MISSED, false);

        DrugOrderScheduleResponse response = DrugOrderScheduleResponse.createFrom(makeSchedule(Collections.singletonList(missedSlot)));

        StageScheduleStatus stage = response.getStageSchedules().get(0);
        assertTrue("Expected allAttended=true for a MISSED slot", stage.getAllAttended());
    }

    // -----------------------------------------------------------------------
    // administrationStarted flag per stage
    // -----------------------------------------------------------------------

    @Test
    public void shouldSetAdministrationStarted_WhenAnySlotHasAdministration() {
        Slot completedWithAdmin = makeSlot(1, Slot.SlotStatus.COMPLETED, true);

        DrugOrderScheduleResponse response = DrugOrderScheduleResponse.createFrom(makeSchedule(Collections.singletonList(completedWithAdmin)));

        StageScheduleStatus stage = response.getStageSchedules().get(0);
        assertTrue("Expected administrationStarted=true when slot has a MedicationAdministration", stage.getAdministrationStarted());
    }

    @Test
    public void shouldNotSetAdministrationStarted_WhenNoSlotHasAdministration() {
        Slot scheduledNoAdmin = makeSlot(1, Slot.SlotStatus.SCHEDULED, false);

        DrugOrderScheduleResponse response = DrugOrderScheduleResponse.createFrom(makeSchedule(Collections.singletonList(scheduledNoAdmin)));

        StageScheduleStatus stage = response.getStageSchedules().get(0);
        assertFalse("Expected administrationStarted=false when no slot has a MedicationAdministration", stage.getAdministrationStarted());
    }

    // -----------------------------------------------------------------------
    // Top-level allSlotsAttended
    // -----------------------------------------------------------------------

    @Test
    public void shouldPreserveTopLevelAllSlotsAttended_WhenAllStageSlotsAttended() {
        Slot stage1Completed = makeSlot(1, Slot.SlotStatus.COMPLETED, false);
        Slot stage2Completed = makeSlot(2, Slot.SlotStatus.COMPLETED, false);

        DrugOrderScheduleResponse response = DrugOrderScheduleResponse.createFrom(makeSchedule(Arrays.asList(stage1Completed, stage2Completed)));

        assertTrue("Expected top-level allSlotsAttended=true when all slots are COMPLETED", response.getAllSlotsAttended());
    }

    @Test
    public void shouldPreserveTopLevelAllSlotsAttended_False_WhenSomeSlotIsScheduled() {
        Slot completedSlot = makeSlot(1, Slot.SlotStatus.COMPLETED, false);
        Slot scheduledSlot = makeSlot(2, Slot.SlotStatus.SCHEDULED, false);

        DrugOrderScheduleResponse response = DrugOrderScheduleResponse.createFrom(makeSchedule(Arrays.asList(completedSlot, scheduledSlot)));

        assertFalse("Expected top-level allSlotsAttended=false when at least one slot is SCHEDULED", response.getAllSlotsAttended());
    }

    // -----------------------------------------------------------------------
    // isStartTimeFrequencyForStage — VDP scheduling mode detection
    // -----------------------------------------------------------------------

    private Slot makeVdpSlot(Integer sequence, Slot.SlotStatus status, String dosingInstructionsJson) {
        Slot slot = makeSlot(sequence, status, false);
        org.openmrs.DrugOrder drugOrder = new org.openmrs.DrugOrder();
        drugOrder.setDosingInstructions(dosingInstructionsJson);
        slot.setOrder(drugOrder);
        return slot;
    }

    @Test
    public void shouldComputeStartTimeMode_WhenStageIsLoadingDose() {
        String fhirJson = "[{\"sequence\":1,\"timing\":{\"code\":{\"text\":\"Once\"}},"
            + "\"doseAndRate\":[{\"doseQuantity\":{\"value\":5,\"unit\":\"mg\"}}],"
            + "\"extension\":[{\"url\":\"isLoadingDose\",\"valueBoolean\":true}]}]";
        Slot slot = makeVdpSlot(1, Slot.SlotStatus.SCHEDULED, fhirJson);

        DrugOrderScheduleResponse response = DrugOrderScheduleResponse.createFrom(makeSchedule(Collections.singletonList(slot)));

        StageScheduleStatus stage = response.getStageSchedules().get(0);
        assertNotNull(stage.getSlotStartTime());
        assertNull(stage.getDayWiseSlotsStartTime());
    }

    @Test
    public void shouldComputeStartTimeMode_WhenFrequencyIsInStartTimeFrequencies() {
        String fhirJson = "[{\"sequence\":2,\"timing\":{\"code\":{\"text\":\"Once a day\"},"
            + "\"repeat\":{\"duration\":3,\"durationUnit\":\"d\"}},"
            + "\"doseAndRate\":[{\"doseQuantity\":{\"value\":3,\"unit\":\"mg\"}}],"
            + "\"extension\":[{\"url\":\"isLoadingDose\",\"valueBoolean\":false}]}]";
        Slot slot = makeVdpSlot(2, Slot.SlotStatus.SCHEDULED, fhirJson);

        DrugOrderScheduleResponse response = DrugOrderScheduleResponse.createFrom(makeSchedule(Collections.singletonList(slot)));

        StageScheduleStatus stage = response.getStageSchedules().get(0);
        assertNotNull(stage.getSlotStartTime());
        assertNull(stage.getDayWiseSlotsStartTime());
    }
}
