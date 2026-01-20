package org.openmrs.module.ipd.api.service;

import org.openmrs.Visit;
import org.openmrs.api.APIException;
import org.openmrs.api.OpenmrsService;
import org.openmrs.module.ipd.api.model.CareTeam;



public interface CareTeamService extends OpenmrsService {

    CareTeam saveCareTeam(CareTeam careTeam) throws APIException;

    CareTeam getCareTeamByVisit(Visit visit) throws APIException;

}
