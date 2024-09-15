package org.openmrs.module.ipd.api.dao.impl;

import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.openmrs.Location;
import org.openmrs.Provider;
import org.openmrs.module.ipd.api.dao.WardDAO;
import org.openmrs.module.ipd.api.model.AdmittedPatient;
import org.openmrs.module.ipd.api.model.WardPatientsSummary;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import org.hibernate.query.Query;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class HibernateWardDAO implements WardDAO {

    private static final Logger log = LoggerFactory.getLogger(HibernateWardDAO.class);

    private SessionFactory sessionFactory;

    public void setSessionFactory(SessionFactory sessionFactory) {
        this.sessionFactory = sessionFactory;
    }

    private static String generateOrderByClauseForSorting(String sortBy) {
        String orderBy = " ";

        sortBy = sortBy != null ? sortBy : "default";

        switch (sortBy) {
            case "bedNumber":
                orderBy += "ORDER BY assignment.bed.bedNumber ";
                break;
            default:
                orderBy += "ORDER BY assignment.startDatetime desc ";
                break;
        }
        return orderBy;
    }

    @Override
    public List<AdmittedPatient> getAdmittedPatients(Location location, Provider provider, Date dateTime, String sortBy) {
        Session session = this.sessionFactory.getCurrentSession();
        try {
            String queryString = "select NEW org.openmrs.module.ipd.api.model.AdmittedPatient(assignment," +
                    "(COUNT(DISTINCT o.orderId) - COUNT (DISTINCT s.order.orderId)), careTeam)" +
                    "from org.openmrs.module.bedmanagement.entity.BedPatientAssignment assignment " +
                    "JOIN org.openmrs.Visit v on v.patient = assignment.patient " +
                    "JOIN org.openmrs.Encounter e on e.visit = v " +
                    "LEFT JOIN CareTeam careTeam on careTeam.visit = v " +
                    "JOIN org.openmrs.module.bedmanagement.entity.BedLocationMapping locmap on locmap.bed = assignment.bed " +
                    "JOIN org.openmrs.Location l on locmap.location = l " +
                    "LEFT JOIN careTeam.participants ctp ON ctp.voided = 0 " +
                    "LEFT JOIN org.openmrs.Order o on o.encounter = e and o.dateStopped is null and o.action!='DISCONTINUE' and o.careSetting.careSettingId = 2 " +
                    "LEFT JOIN Slot s on s.order = o " +
                    "where assignment.endDatetime is null and v.stopDatetime is null ";

            if(location != null){
                queryString += "and l.parentLocation = :location ";
            }

            if (provider != null) {
                queryString += "and ctp.provider = :provider ";
            }

            if (dateTime != null) {
                queryString += "and :dateTime between ctp.startTime and ctp.endTime ";
            }

            String groupBy = " GROUP BY assignment.patient, v ";

            String orderBy = generateOrderByClauseForSorting(sortBy);

            String finalQuery = queryString + groupBy + orderBy;

            Query query = session.createQuery(finalQuery);

            if(location != null) {
                query.setParameter("location", location);
            }

            if (provider != null) {
                query.setParameter("provider", provider);
            }

            if (dateTime != null) {
                query.setParameter("dateTime", dateTime);
            }

            return query.getResultList();
        }
        catch (Exception ex){
            log.error("Exception at WardDAO getAdmittedPatients ",ex.getStackTrace());
        }
        return new ArrayList<>();
    }

    @Override
    public WardPatientsSummary getWardPatientSummary(Location location, Provider provider, Date dateTime) {
        Session session = this.sessionFactory.getCurrentSession();
        try {
            Query totalPatientsQuery = session.createQuery(
                 "SELECT COUNT(assignment) " +
                    "FROM org.openmrs.module.bedmanagement.entity.BedPatientAssignment assignment " +
                    "JOIN org.openmrs.module.bedmanagement.entity.BedLocationMapping locmap ON locmap.bed = assignment.bed " +
                    "JOIN org.openmrs.Location l ON locmap.location = l " +
                    "JOIN org.openmrs.Visit v ON v.patient = assignment.patient " +
                    "WHERE assignment.endDatetime IS NULL AND v.stopDatetime IS NULL AND l.parentLocation = :location"
            );

            totalPatientsQuery.setParameter("location", location);

            Long totalPatients = (Long) totalPatientsQuery.uniqueResult();

            Query totalProviderPatientsQuery = session.createQuery(
                 "SELECT COUNT(DISTINCT CASE WHEN ctp.provider = :provider THEN assignment.patient ELSE null END) " +
                    "FROM org.openmrs.module.bedmanagement.entity.BedPatientAssignment assignment " +
                    "JOIN org.openmrs.module.bedmanagement.entity.BedLocationMapping locmap ON locmap.bed = assignment.bed " +
                    "JOIN org.openmrs.Location l ON locmap.location = l " +
                    "JOIN org.openmrs.Visit v ON v.patient = assignment.patient " +
                    "LEFT JOIN CareTeam careTeam ON careTeam.patient = v.patient " +
                    "LEFT JOIN careTeam.participants ctp ON ctp.voided = 0 " +
                    "WHERE assignment.endDatetime IS NULL AND v.stopDatetime IS NULL AND l.parentLocation = :location " +
                    "AND (ctp.provider = :provider AND :dateTime BETWEEN ctp.startTime AND ctp.endTime)"
            );

            totalProviderPatientsQuery.setParameter("location", location);
            totalProviderPatientsQuery.setParameter("provider", provider);
            totalProviderPatientsQuery.setParameter("dateTime", dateTime);

            Long totalProviderPatients = (Long) totalProviderPatientsQuery.uniqueResult();

            return new WardPatientsSummary(totalPatients, totalProviderPatients);
        } catch (Exception e) {
            log.error("Exception at WardDAO getAdmittedPatients ", e);
        }

        return new WardPatientsSummary();
    }

    @Override
    public List<AdmittedPatient> searchAdmittedPatients(Location location, List<String> searchKeys, String searchValue, String sortBy) {
        try {
            Session session = sessionFactory.getCurrentSession();

            String selectQuery = "select NEW org.openmrs.module.ipd.api.model.AdmittedPatient(assignment, " +
                    "(COUNT(DISTINCT o.orderId) - COUNT(DISTINCT s.order.orderId)), careTeam) " +
                    "from org.openmrs.module.bedmanagement.entity.BedPatientAssignment assignment " +
                    "JOIN org.openmrs.Visit v on v.patient = assignment.patient " +
                    "JOIN org.openmrs.Patient p on assignment.patient = p " +
                    "JOIN org.openmrs.Person pr on pr.personId = p.patientId " +
                    "JOIN org.openmrs.Encounter e on e.visit = v " +
                    "LEFT JOIN CareTeam careTeam on careTeam.visit = v " +
                    "JOIN org.openmrs.module.bedmanagement.entity.BedLocationMapping locmap on locmap.bed = assignment.bed " +
                    "JOIN org.openmrs.Location l on locmap.location = l " +
                    "LEFT JOIN org.openmrs.Order o on o.encounter = e and o.dateStopped is null and o.action!='DISCONTINUE' and o.careSetting.careSettingId = 2 " +
                    "LEFT JOIN Slot s on s.order = o ";


            // Construct additional joins and where clause based on search keys
            StringBuilder additionalJoins = new StringBuilder();
            StringBuilder whereClause = new StringBuilder();
            generateSQLSearchConditions(searchKeys, additionalJoins, whereClause, searchValue);

            // Construct group by clause
            String groupBy = " GROUP BY assignment.patient, v ";

            String orderBy = generateOrderByClauseForSorting(sortBy);

            // Create query
            Query query = session.createQuery(selectQuery + additionalJoins + whereClause + groupBy + orderBy);

            // Set parameters
            query.setParameter("location", location);
            setQueryParameters(query, searchKeys, searchValue);

            return query.getResultList();
        } catch (Exception ex) {
            log.error("Exception at WardDAO searching ", ex.getMessage());
            return new ArrayList<>();
        }
    }


    private void generateSQLSearchConditions(List<String> searchKeys, StringBuilder additionalJoins, StringBuilder whereClause, String searchValue) {
        whereClause.append("where (assignment.endDatetime is null and v.stopDatetime is null and l.parentLocation = :location)");
        if (searchKeys != null && !searchKeys.isEmpty()) {
            whereClause.append(" and (");
            for (int i = 0; i < searchKeys.size(); i++) {
                switch (searchKeys.get(i)) {
                    case "bedNumber":
                        whereClause.append(" assignment.bed.bedNumber LIKE :bedNumber ");
                        break;
                    case "patientIdentifier":
                        additionalJoins.append(" JOIN p.identifiers pi ");
                        whereClause.append(" pi.identifier LIKE :patientIdentifier ");
                        break;
                    case "patientName":
                        additionalJoins.append(" JOIN pr.names prn ");
                        whereClause.append(" (");

                        String[] nameParts = searchValue.split("\\s+");
                        for (int j = 0; j < nameParts.length; j++) {
                            if (j > 0) whereClause.append(" and ");
                            whereClause.append(" (prn.givenName LIKE :namePart" + j +
                                    " or prn.middleName LIKE :namePart" + j +
                                    " or prn.familyName LIKE :namePart" + j + ") ");
                        }
                        whereClause.append(" )");
                        break;
                }
                if (i < searchKeys.size() - 1) {
                    whereClause.append(" or ");
                }
            }
            whereClause.append(" ) ");
        }
    }

    private void setQueryParameters(Query query, List<String> searchKeys, String searchValue) {
        if (searchKeys != null && searchValue != null && !searchValue.isEmpty()) {
            if (searchKeys.contains("bedNumber")) {
                query.setParameter("bedNumber", "%" + searchValue + "%");
            }
            if (searchKeys.contains("patientIdentifier")) {
                query.setParameter("patientIdentifier", "%" + searchValue + "%");
            }
            if (searchKeys.contains("patientName")) {
                String[] nameParts = searchValue.split("\\s+");
                for (int i = 0; i < nameParts.length; i++) {
                    query.setParameter("namePart" + i, "%" + nameParts[i] + "%");
                }
            }
        }
    }

    private StringBuilder appendORIfMoreSearchKeysPresent(int i,int size,StringBuilder whereClause){
        if (size==(i+1)){
            return whereClause;
        }
        return whereClause.append(" or ");
    }



}
