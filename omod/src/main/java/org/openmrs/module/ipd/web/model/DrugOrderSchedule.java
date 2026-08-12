package org.openmrs.module.ipd.web.model;

import lombok.*;
import org.openmrs.module.ipd.api.model.Slot;
import org.openmrs.module.ipd.web.model.StageScheduleStatus;
import org.openmrs.module.ipd.web.contract.CrossingSlotDTO;

import java.util.List;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DrugOrderSchedule {

    private List<Long> firstDaySlotsStartTime;
    private List<Long> dayWiseSlotsStartTime;
    private List<Long> remainingDaySlotsStartTime;
    private List<CrossingSlotDTO> crossingSlots;
    private Long slotStartTime;
    private List<Slot> slots;
    private String notes;
    private List<StageScheduleStatus> stageSchedules;
    private Boolean isUpdateCompleteSchedule;
}
