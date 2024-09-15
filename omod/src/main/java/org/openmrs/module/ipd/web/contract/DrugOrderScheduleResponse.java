package org.openmrs.module.ipd.web.contract;

import lombok.*;
import org.openmrs.module.ipd.api.model.Slot;
import org.openmrs.module.ipd.web.model.DrugOrderSchedule;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DrugOrderScheduleResponse {

    private List<Long> firstDaySlotsStartTime;
    private List<Long> dayWiseSlotsStartTime;
    private List<Long> remainingDaySlotsStartTime;
    private Long slotStartTime;
    private Boolean medicationAdministrationStarted;
    private Boolean pendingSlotsAvailable;
    private Boolean allSlotsAttended;
    private String notes;

    public static DrugOrderScheduleResponse createFrom(DrugOrderSchedule drugOrderSchedule){
        return DrugOrderScheduleResponse.builder().
                firstDaySlotsStartTime(drugOrderSchedule.getFirstDaySlotsStartTime()).
                dayWiseSlotsStartTime(drugOrderSchedule.getDayWiseSlotsStartTime()).
                remainingDaySlotsStartTime(drugOrderSchedule.getRemainingDaySlotsStartTime()).
                slotStartTime(drugOrderSchedule.getSlotStartTime()).
                medicationAdministrationStarted(drugOrderSchedule.getSlots().stream().anyMatch(slot -> slot.getMedicationAdministration() != null)).
                allSlotsAttended(!(drugOrderSchedule.getSlots().stream().anyMatch(slot -> slot.getStatus().equals(Slot.SlotStatus.SCHEDULED)))).
                pendingSlotsAvailable(drugOrderSchedule.getSlots().stream().anyMatch(slot -> (LocalDateTime.now().isBefore(slot.getStartDateTime()) && slot.getStatus().equals(Slot.SlotStatus.SCHEDULED)))).
                notes(drugOrderSchedule.getSlots().get(0).getNotes()).
                build();
    }
}
