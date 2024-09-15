package org.openmrs.module.ipd.contract;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

@Getter
@AllArgsConstructor
@NoArgsConstructor
public class CareTeamRequest {
    private String patientUuid;
    private List<CareTeamParticipantRequest> careTeamParticipantsRequest;
}
