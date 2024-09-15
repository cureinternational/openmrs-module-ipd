package org.openmrs.module.ipd.api.service.impl;


import org.openmrs.Visit;
import org.openmrs.api.APIException;
import org.openmrs.api.impl.BaseOpenmrsService;
import org.openmrs.module.ipd.api.dao.CareTeamDAO;
import org.openmrs.module.ipd.api.model.CareTeam;
import org.openmrs.module.ipd.api.model.Schedule;
import org.openmrs.module.ipd.api.service.CareTeamService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class CareTeamServiceImpl extends BaseOpenmrsService implements CareTeamService {

    private static final Logger log = LoggerFactory.getLogger(CareTeamServiceImpl.class);

    private final CareTeamDAO careTeamDAO;

    @Autowired
    public CareTeamServiceImpl(CareTeamDAO careTeamDAO) {
        this.careTeamDAO = careTeamDAO;
    }

    @Override
    public CareTeam saveCareTeam(CareTeam careTeam) throws APIException {
        return careTeamDAO.saveCareTeam(careTeam);
    }

    @Override
    public CareTeam getCareTeamByVisit(Visit visit) throws APIException {
        return careTeamDAO.getCareTeamByVisit(visit);
    }
}
