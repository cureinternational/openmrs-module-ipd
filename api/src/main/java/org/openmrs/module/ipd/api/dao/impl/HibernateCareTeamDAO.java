package org.openmrs.module.ipd.api.dao.impl;

import org.hibernate.SessionFactory;
import org.hibernate.query.Query;
import org.openmrs.Visit;
import org.openmrs.api.db.DAOException;
import org.openmrs.module.ipd.api.dao.CareTeamDAO;
import org.openmrs.module.ipd.api.model.CareTeam;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

public class HibernateCareTeamDAO implements CareTeamDAO {

    private static final Logger log = LoggerFactory.getLogger(HibernateCareTeamDAO.class);
    private SessionFactory sessionFactory;

    public void setSessionFactory(SessionFactory sessionFactory) {
        this.sessionFactory = sessionFactory;
    }

    @Override
    public CareTeam saveCareTeam(CareTeam careTeam) throws DAOException {
        sessionFactory.getCurrentSession().saveOrUpdate(careTeam);
        return careTeam;
    }

    @Override
    public CareTeam getCareTeamByVisit(Visit visit) throws DAOException {
        Query query = sessionFactory.getCurrentSession()
                .createQuery("FROM CareTeam careteam " +
                        "WHERE careteam.visit = :visit ");

        query.setParameter("visit", visit);

        return (CareTeam) query.uniqueResult();
    }
}
