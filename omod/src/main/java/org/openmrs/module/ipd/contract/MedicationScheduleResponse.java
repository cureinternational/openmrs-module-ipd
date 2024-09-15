package org.openmrs.module.ipd.contract;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.openmrs.module.ipd.api.model.Schedule;
import org.openmrs.module.ipd.api.model.Slot;

import java.util.List;
import java.util.stream.Collectors;

import static org.openmrs.module.ipd.api.util.DateTimeUtil.convertLocalDateTimeToUTCEpoc;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MedicationScheduleResponse {

    private Integer id;
    private String uuid;
    private String serviceType;
    private String comments;
    private long startDate;
    private Object endDate;
    private List<MedicationSlotResponse> slots;
    public static MedicationScheduleResponse createFrom(Schedule schedule, List<Slot> slots) {

        return MedicationScheduleResponse.builder()
                .id(schedule.getId())
                .uuid(schedule.getUuid())
                .serviceType(schedule.getServiceType().getName().getName())
                .comments(schedule.getComments())
                .startDate(convertLocalDateTimeToUTCEpoc(schedule.getStartDate()))
                .endDate(schedule.getEndDate() != null ? convertLocalDateTimeToUTCEpoc(schedule.getEndDate()) : null)
                .slots(slots.stream().map(MedicationSlotResponse::createFrom).collect(Collectors.toList()))
                .build();

    }

}

