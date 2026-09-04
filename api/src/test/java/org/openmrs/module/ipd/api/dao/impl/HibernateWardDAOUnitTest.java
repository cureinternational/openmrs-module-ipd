package org.openmrs.module.ipd.api.dao.impl;

import org.junit.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class HibernateWardDAOUnitTest {

    private static final String ACTIVE_ORDER_JOIN =
            "LEFT JOIN org.openmrs.Order o on o.patient = assignment.patient " +
                    "and o.voided = false and o.action!='DISCONTINUE' " +
                    "and ((o.dateStopped is null and (o.autoExpireDate is null or o.autoExpireDate >= :now)) " +
                    "or o.dateStopped >= :now)";

    @Test
    public void shouldJoinOrdersByPatientInsteadOfEncounterInAdmittedPatientsQuery() {
        String query = HibernateWardDAO.getAdmittedPatientsSelectClause();

        assertTrue(query.contains("o.patient = assignment.patient"));
        assertFalse(query.contains("o.encounter"));
    }

    @Test
    public void shouldJoinOrdersByPatientInsteadOfEncounterInSearchAdmittedPatientsQuery() {
        String query = HibernateWardDAO.getSearchAdmittedPatientsSelectClause();

        assertTrue(query.contains("o.patient = assignment.patient"));
        assertFalse(query.contains("o.encounter"));
    }

    @Test
    public void shouldExcludeVoidedAndDiscontinuedOrdersInAdmittedPatientsQuery() {
        String query = HibernateWardDAO.getAdmittedPatientsSelectClause();

        assertTrue(query.contains("o.voided = false"));
        assertTrue(query.contains("o.action!='DISCONTINUE'"));
    }

    @Test
    public void shouldExcludeVoidedAndDiscontinuedOrdersInSearchAdmittedPatientsQuery() {
        String query = HibernateWardDAO.getSearchAdmittedPatientsSelectClause();

        assertTrue(query.contains("o.voided = false"));
        assertTrue(query.contains("o.action!='DISCONTINUE'"));
    }

    @Test
    public void shouldUseActiveOrderDateLogicInAdmittedPatientsQuery() {
        String query = HibernateWardDAO.getAdmittedPatientsSelectClause();

        assertTrue(query.contains("o.dateStopped is null"));
        assertTrue(query.contains("o.autoExpireDate is null"));
        assertTrue(query.contains("o.autoExpireDate >= :now"));
        assertTrue(query.contains("o.dateStopped >= :now"));
        assertTrue(query.contains(ACTIVE_ORDER_JOIN));
    }

    @Test
    public void shouldUseActiveOrderDateLogicInSearchAdmittedPatientsQuery() {
        String query = HibernateWardDAO.getSearchAdmittedPatientsSelectClause();

        assertTrue(query.contains("o.dateStopped is null"));
        assertTrue(query.contains("o.autoExpireDate is null"));
        assertTrue(query.contains("o.autoExpireDate >= :now"));
        assertTrue(query.contains("o.dateStopped >= :now"));
        assertTrue(query.contains(ACTIVE_ORDER_JOIN));
    }

    @Test
    public void shouldSubtractSlottedOrdersInAdmittedPatientsQuery() {
        String query = HibernateWardDAO.getAdmittedPatientsSelectClause();

        assertTrue(query.contains("COUNT(DISTINCT o.orderId)"));
        assertTrue(query.contains("COUNT (DISTINCT s.order.orderId)"));
        assertTrue(query.contains("LEFT JOIN Slot s on s.order = o "));
    }

    @Test
    public void shouldSubtractSlottedOrdersInSearchAdmittedPatientsQuery() {
        String query = HibernateWardDAO.getSearchAdmittedPatientsSelectClause();

        assertTrue(query.contains("COUNT(DISTINCT o.orderId)"));
        assertTrue(query.contains("COUNT(DISTINCT s.order.orderId)"));
        assertTrue(query.contains("LEFT JOIN Slot s on s.order = o "));
    }

    @Test
    public void shouldKeepFullActiveOrderRuleInBothQueryBuilders() {
        assertTrue(HibernateWardDAO.getAdmittedPatientsSelectClause().contains(ACTIVE_ORDER_JOIN));
        assertTrue(HibernateWardDAO.getSearchAdmittedPatientsSelectClause().contains(ACTIVE_ORDER_JOIN));
    }
}
