package org.openmrs.module.ipd.web.service.impl;

import org.openmrs.Patient;
import org.openmrs.Visit;
import org.openmrs.api.ConceptService;
import org.openmrs.api.PatientService;
import org.openmrs.api.VisitService;
import org.openmrs.module.ipd.api.model.CareTeam;
import org.openmrs.module.ipd.api.service.CareTeamService;
import org.openmrs.module.ipd.web.contract.CareTeamRequest;
import org.openmrs.module.ipd.web.factory.CareTeamFactory;
import org.openmrs.module.ipd.web.service.IPDCareTeamService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class IPDCareTeamServiceImpl implements IPDCareTeamService {

    private final CareTeamService careTeamService;
    private final CareTeamFactory careTeamFactory;
    private final ConceptService conceptService;
    private final VisitService visitService;
    private final PatientService patientService;

    @Autowired
    public IPDCareTeamServiceImpl(CareTeamService careTeamService, CareTeamFactory careTeamFactory, ConceptService conceptService, VisitService visitService, PatientService patientService) {
        this.careTeamService = careTeamService;
        this.careTeamFactory = careTeamFactory;
        this.conceptService = conceptService;
        this.visitService = visitService;
        this.patientService = patientService;
    }

    @Override
    public CareTeam saveCareTeamParticipants(CareTeamRequest careTeamRequest) {
        Patient patient = patientService.getPatientByUuid(careTeamRequest.getPatientUuid());
        Visit visit = visitService.getActiveVisitsByPatient(patient).get(0);
        CareTeam careTeam = careTeamService.getCareTeamByVisit(visit);
        if (careTeam==null || careTeam.getCareTeamId()==null){
            careTeam = careTeamFactory.createCareTeamFromRequest(careTeamRequest,patient,visit);
        }
        else {
            careTeam = careTeamFactory.updateCareTeamFromRequest(careTeamRequest, careTeam);
        }
        return careTeamService.saveCareTeam(careTeam);
    }
}
