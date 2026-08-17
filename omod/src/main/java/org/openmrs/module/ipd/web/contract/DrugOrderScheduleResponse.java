package org.openmrs.module.ipd.web.contract;

import lombok.*;
import org.openmrs.module.ipd.api.model.Slot;
import org.openmrs.module.ipd.web.model.DrugOrderSchedule;
import org.openmrs.module.ipd.web.model.StageScheduleStatus;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DrugOrderScheduleResponse {

    private List<Long> firstDaySlotsStartTime;
    private List<Long> dayWiseSlotsStartTime;
    private List<Long> remainingDaySlotsStartTime;
    private List<CrossingSlotDTO> crossingSlots;
    private Long slotStartTime;
    private Boolean medicationAdministrationStarted;
    private Boolean pendingSlotsAvailable;
    private Boolean allSlotsAttended;
    private String notes;
    private List<StageScheduleStatus> stageSchedules;
    private Boolean isUpdateCompleteSchedule;

    public static DrugOrderScheduleResponse createFrom(DrugOrderSchedule drugOrderSchedule) {
        List<Slot> slots = drugOrderSchedule.getSlots() != null ? drugOrderSchedule.getSlots() : Collections.emptyList();

        return DrugOrderScheduleResponse.builder()
            .firstDaySlotsStartTime(drugOrderSchedule.getFirstDaySlotsStartTime())
            .dayWiseSlotsStartTime(drugOrderSchedule.getDayWiseSlotsStartTime())
            .remainingDaySlotsStartTime(drugOrderSchedule.getRemainingDaySlotsStartTime())
            .crossingSlots(drugOrderSchedule.getCrossingSlots())
            .slotStartTime(drugOrderSchedule.getSlotStartTime())
            .medicationAdministrationStarted(slots.stream().anyMatch(slot -> slot.getMedicationAdministration() != null))
            .allSlotsAttended(!slots.stream().anyMatch(slot -> slot.getStatus().equals(Slot.SlotStatus.SCHEDULED)))
            .pendingSlotsAvailable(slots.stream().anyMatch(slot ->
                slot.getStartDateTime() != null &&
                LocalDateTime.now().isBefore(slot.getStartDateTime()) &&
                slot.getStatus().equals(Slot.SlotStatus.SCHEDULED)))
            .notes(slots.isEmpty() ? null : slots.get(0).getNotes())
            .stageSchedules(drugOrderSchedule.getStageSchedules())
            .isUpdateCompleteSchedule(drugOrderSchedule.getIsUpdateCompleteSchedule())
            .build();
    }
}
