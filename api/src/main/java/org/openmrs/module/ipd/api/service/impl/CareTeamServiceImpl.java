package org.openmrs.module.ipd.api.service.impl;


import org.openmrs.Visit;
import org.openmrs.api.APIException;
import org.openmrs.api.context.Context;
import org.openmrs.api.impl.BaseOpenmrsService;
import org.openmrs.module.ipd.api.dao.CareTeamDAO;
import org.openmrs.module.ipd.api.model.CareTeam;
import org.openmrs.module.ipd.api.model.CareTeamParticipant;
import org.openmrs.module.ipd.api.service.CareTeamService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.annotation.Transactional;
import java.util.Date;
import java.util.List;

@Transactional
public class CareTeamServiceImpl extends BaseOpenmrsService implements CareTeamService {

    private static final Logger log = LoggerFactory.getLogger(CareTeamServiceImpl.class);

    private CareTeamDAO careTeamDAO;

    public void setCareTeamDAO(CareTeamDAO careTeamDAO) {
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

    @Override
    public void voidCareTeamParticipant(CareTeam careTeam, CareTeamParticipant participant, String voidReason) throws APIException {
        participant.setVoided(true);
        participant.setVoidedBy(Context.getAuthenticatedUser());
        participant.setDateVoided(new Date());
        participant.setVoidReason(voidReason);
        // Note: OpenMRS VoidHandler AOP advice will automatically set changedBy and dateChanged
        careTeamDAO.saveCareTeam(careTeam);
    }

    @Override
    public int unbookmarkAllActivePatients() throws APIException {
        log.info("Starting unbookmark all active patients at shift end");

        int totalUnbookmarked = 0;

        // Load all CareTeam aggregate roots
        List<CareTeam> allTeams = careTeamDAO.getAllCareTeams();

        for (CareTeam careTeam : allTeams) {
            // Void participants through proper void method (respects OpenMRS audit conventions)
            for (CareTeamParticipant participant : careTeam.getParticipants()) {
                if (!participant.getVoided()) {
                    voidCareTeamParticipant(careTeam, participant, "Automatically unbookmarked at shift end");
                    totalUnbookmarked++;
                    log.debug("Unbookmarked participant {} from care team: {}",
                        participant.getUuid(), careTeam.getUuid());
                }
            }
        }

        log.info("Completed unbookmark all active patients. Total unbookmarked: {}", totalUnbookmarked);
        return totalUnbookmarked;
    }
}
