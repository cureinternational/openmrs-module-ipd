package org.openmrs.module.ipd.web.service;

import org.openmrs.module.ipd.api.model.CareTeam;
import org.openmrs.module.ipd.web.contract.CareTeamRequest;

public interface IPDCareTeamService {

    CareTeam saveCareTeamParticipants(CareTeamRequest careTeamRequest);
}
