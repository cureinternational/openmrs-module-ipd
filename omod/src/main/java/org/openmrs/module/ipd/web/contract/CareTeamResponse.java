package org.openmrs.module.ipd.web.contract;

import lombok.Builder;
import lombok.Getter;
import org.openmrs.module.ipd.api.model.CareTeam;

import java.util.List;
import java.util.stream.Collectors;

@Getter
@Builder
public class CareTeamResponse {

    private String uuid;
    private String patientUuid;
    private List<CareTeamParticipantResponse> participants;

    public static CareTeamResponse createFrom(CareTeam careTeam){
        return CareTeamResponse.builder().
                uuid(careTeam.getUuid()).
                patientUuid(careTeam.getPatient().getUuid()).
                participants(careTeam.getParticipants().stream().
                        filter(careTeamParticipant -> !careTeamParticipant.getVoided()).
                        map(CareTeamParticipantResponse::createFrom).collect(Collectors.toList())).
                build();
    }

}
