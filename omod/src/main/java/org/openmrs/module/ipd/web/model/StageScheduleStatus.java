package org.openmrs.module.ipd.web.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StageScheduleStatus {
    private Integer variableDosageSequence;
    private Boolean isScheduled;
    private Boolean administrationStarted;
    private Boolean allAttended;
    private Boolean pendingSlotsAvailable;
    private String notes;
    private Long slotStartTime;
    private java.util.List<Long> firstDaySlotsStartTime;
    private java.util.List<Long> dayWiseSlotsStartTime;
    private java.util.List<Long> remainingDaySlotsStartTime;
}
