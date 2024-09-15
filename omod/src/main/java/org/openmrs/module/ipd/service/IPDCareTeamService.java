package org.openmrs.module.ipd.service;

import org.openmrs.module.ipd.api.model.CareTeam;
import org.openmrs.module.ipd.contract.CareTeamParticipantRequest;
import org.openmrs.module.ipd.contract.CareTeamRequest;

import java.util.List;

public interface IPDCareTeamService {

    CareTeam saveCareTeamParticipants(CareTeamRequest careTeamRequest);
}
