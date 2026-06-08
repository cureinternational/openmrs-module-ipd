package org.openmrs.module.ipd.web.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StageScheduleStatus {
    private Integer variableDosageSequence;
    @JsonProperty("isScheduled")
    private Boolean isScheduled;
    private Boolean administrationStarted;
    private Boolean allAttended;
    private Boolean pendingSlotsAvailable;
    private String notes;
    private Long slotStartTime;
    private List<Long> firstDaySlotsStartTime;
    private List<Long> dayWiseSlotsStartTime;
    private List<Long> remainingDaySlotsStartTime;
}
