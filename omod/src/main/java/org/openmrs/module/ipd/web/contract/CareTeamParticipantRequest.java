package org.openmrs.module.ipd.web.contract;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.Date;
import java.util.List;
import java.util.concurrent.TimeUnit;

@AllArgsConstructor
@NoArgsConstructor
@Getter
@Builder
public class CareTeamParticipantRequest {

    private String uuid;
    private Long startTime;
    private Long endTime;
    private String providerUuid;
    private Boolean voided;

}
