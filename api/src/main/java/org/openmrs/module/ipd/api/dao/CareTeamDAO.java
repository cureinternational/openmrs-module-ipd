package org.openmrs.module.ipd.api.dao;

import org.openmrs.Visit;
import org.openmrs.api.db.DAOException;
import org.openmrs.module.ipd.api.model.CareTeam;
import org.openmrs.module.ipd.api.model.CareTeamParticipant;
import java.util.Date;
import java.util.List;

public interface CareTeamDAO {

    CareTeam saveCareTeam(CareTeam careTeam) throws DAOException;

    CareTeam getCareTeamByVisit(Visit visit) throws DAOException;

    /**
     * Get all CareTeam aggregate roots.
     * Used for operations that need to iterate over all care teams and their participants.
     *
     * @return List of all CareTeam objects
     * @throws DAOException if error occurs
     */
    List<CareTeam> getAllCareTeams() throws DAOException;

    List<CareTeamParticipant> getActiveParticipants(Date asOf) throws DAOException;

    CareTeamParticipant saveParticipant(CareTeamParticipant participant) throws DAOException;

}
