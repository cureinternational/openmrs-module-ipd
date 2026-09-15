package org.openmrs.module.ipd.api.service;

import org.openmrs.Visit;
import org.openmrs.api.APIException;
import org.openmrs.module.ipd.api.model.CareTeam;
import org.openmrs.module.ipd.api.model.CareTeamParticipant;
import org.openmrs.api.OpenmrsService;



public interface CareTeamService extends OpenmrsService {

    CareTeam saveCareTeam(CareTeam careTeam) throws APIException;

    CareTeam getCareTeamByVisit(Visit visit) throws APIException;

    void voidCareTeamParticipant(CareTeam careTeam, CareTeamParticipant participant, String voidReason) throws APIException;

    int unbookmarkAllActivePatients() throws APIException;

}
