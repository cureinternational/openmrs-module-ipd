package org.openmrs.module.ipd.model;

import lombok.*;
import org.openmrs.module.ipd.api.model.Slot;

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
    private Long slotStartTime;
    private List<Slot> slots;
    private String notes;
}
