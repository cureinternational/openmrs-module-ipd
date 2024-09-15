package org.openmrs.module.ipd.api.service;

import org.openmrs.Visit;
import org.openmrs.api.APIException;
import org.openmrs.module.ipd.api.model.CareTeam;
import org.openmrs.module.ipd.api.model.Schedule;
import org.springframework.stereotype.Service;
import org.openmrs.api.OpenmrsService;



@Service
public interface CareTeamService extends OpenmrsService {

    CareTeam saveCareTeam(CareTeam careTeam) throws APIException;

    CareTeam getCareTeamByVisit(Visit visit) throws APIException;

}
