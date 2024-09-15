package org.openmrs.module.ipd.contract;

import lombok.Builder;
import lombok.Getter;
import org.openmrs.module.ipd.api.model.CareTeamParticipant;
import org.openmrs.module.ipd.api.util.DateTimeUtil;
import org.openmrs.module.webservices.rest.web.ConversionUtil;
import org.openmrs.module.webservices.rest.web.representation.Representation;

@Getter
@Builder
public class CareTeamParticipantResponse {

    private String uuid;
    private Object provider;
    private Long startTime;
    private Long endTime;
    private Boolean voided;

    public static CareTeamParticipantResponse createFrom(CareTeamParticipant careTeamParticipant) {
        return CareTeamParticipantResponse.builder().
                uuid(careTeamParticipant.getUuid()).
                provider(ConversionUtil.convertToRepresentation(careTeamParticipant.getProvider(), Representation.REF)).
                startTime(careTeamParticipant.getStartTime().getTime()).
                endTime(careTeamParticipant.getEndTime().getTime()).
                voided(careTeamParticipant.getVoided()).
                build();
    }

}
