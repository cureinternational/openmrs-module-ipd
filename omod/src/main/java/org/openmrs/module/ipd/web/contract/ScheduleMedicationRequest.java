package org.openmrs.module.ipd.web.contract;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import org.openmrs.module.ipd.api.model.ServiceType;
import org.openmrs.module.ipd.api.model.Slot;
import org.openmrs.module.ipd.api.util.DateTimeUtil;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import static org.openmrs.module.ipd.api.util.DateTimeUtil.convertEpocUTCToLocalTimeZone;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ScheduleMedicationRequest {

    private String patientUuid;
    private String orderUuid;
    private String providerUuid;
    private String comments;
    private Long slotStartTime;
    private List<Long> firstDaySlotsStartTime;
    private List<Long> dayWiseSlotsStartTime;
    private List<Long> remainingDaySlotsStartTime;
    private List<CrossingSlotContract> crossingSlots;
    private MedicationFrequency medicationFrequency;
    private ServiceType serviceType;
    private Integer variableDosageSequence;

    public enum MedicationFrequency {
        START_TIME_DURATION_FREQUENCY,
        FIXED_SCHEDULE_FREQUENCY
    }

    public LocalDateTime getSlotStartTimeAsLocaltime() {
        return slotStartTime != null ? convertEpocUTCToLocalTimeZone(slotStartTime): null;
    }

    public List<LocalDateTime> getFirstDaySlotsStartTimeAsLocalTime() {
        return firstDaySlotsStartTime != null ? firstDaySlotsStartTime.stream().map(DateTimeUtil::convertEpocUTCToLocalTimeZone).collect(Collectors.toList()) : null;
    }

    public List<LocalDateTime> getDayWiseSlotsStartTimeAsLocalTime() {
        return dayWiseSlotsStartTime != null ? dayWiseSlotsStartTime.stream().map(DateTimeUtil::convertEpocUTCToLocalTimeZone).collect(Collectors.toList()) : null;
    }

    public List<LocalDateTime> getRemainingDaySlotsStartTimeAsLocalTime() {
        return remainingDaySlotsStartTime != null ? remainingDaySlotsStartTime.stream().map(DateTimeUtil::convertEpocUTCToLocalTimeZone).collect(Collectors.toList()) : null;
    }

    public List<LocalDateTime> getCrossingSlotsStartTimeAsLocalTime(Boolean recurring, Slot.SourceBucket sourceBucket) {
        if (crossingSlots == null) return Collections.emptyList();
        return crossingSlots.stream()
                .filter(s -> s.getEpoch() != null)
                .filter(s -> recurring == null || recurring.equals(s.getRecurring()))
                .filter(s -> sourceBucket == null || sourceBucket.equals(s.getSourceBucket()))
                .map(s -> DateTimeUtil.convertEpocUTCToLocalTimeZone(s.getEpoch()))
                .collect(Collectors.toList());
    }
}
