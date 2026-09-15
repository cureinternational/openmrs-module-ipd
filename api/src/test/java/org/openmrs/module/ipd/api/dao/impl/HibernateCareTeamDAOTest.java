package org.openmrs.module.ipd.api.dao.impl;

import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.query.Query;
import org.junit.Before;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.openmrs.Provider;
import org.openmrs.module.ipd.api.model.CareTeam;
import org.openmrs.module.ipd.api.model.CareTeamParticipant;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class HibernateCareTeamDAOTest {

    @Mock
    private SessionFactory sessionFactory;

    @InjectMocks
    private HibernateCareTeamDAO hibernateCareTeamDAO;

    private Session mockSession;
    private Query mockQuery;

    @Before
    public void setup() {
        MockitoAnnotations.initMocks(this);
        mockSession = mock(Session.class);
        mockQuery = mock(Query.class);
        when(sessionFactory.getCurrentSession()).thenReturn(mockSession);
        when(mockSession.createQuery(anyString())).thenReturn(mockQuery);
    }

    @Test
    public void shouldGetAllCareTeams() {
        // FIX: Add missing test for getAllCareTeams()
        org.openmrs.module.ipd.api.model.CareTeam careTeam1 = createCareTeam();
        org.openmrs.module.ipd.api.model.CareTeam careTeam2 = createCareTeam();

        List<org.openmrs.module.ipd.api.model.CareTeam> careTeams = new ArrayList<>();
        careTeams.add(careTeam1);
        careTeams.add(careTeam2);

        when(mockQuery.list()).thenReturn(careTeams);

        List<org.openmrs.module.ipd.api.model.CareTeam> result = hibernateCareTeamDAO.getAllCareTeams();

        assertEquals("Should return 2 care teams", 2, result.size());
        assertNotNull("First care team should not be null", result.get(0));
        assertNotNull("Second care team should not be null", result.get(1));
    }

    @Test
    public void shouldReturnEmptyListWhenNoCareTeams() {
        when(mockQuery.list()).thenReturn(new ArrayList<>());

        List<org.openmrs.module.ipd.api.model.CareTeam> result = hibernateCareTeamDAO.getAllCareTeams();

        assertEquals("Should return empty list", 0, result.size());
    }

    @Test
    public void shouldGetActiveParticipantsBeforeShiftEnd() {
        CareTeamParticipant participant1 = createParticipant(1, false);
        CareTeamParticipant participant2 = createParticipant(2, false);

        List<CareTeamParticipant> participants = new ArrayList<>();
        participants.add(participant1);
        participants.add(participant2);

        when(mockQuery.setParameter(anyString(), org.mockito.ArgumentMatchers.any())).thenReturn(mockQuery);
        when(mockQuery.list()).thenReturn(participants);

        List<CareTeamParticipant> result = hibernateCareTeamDAO.getActiveParticipants(new Date());

        assertEquals("Should return 2 active participants", 2, result.size());
    }

    @Test
    public void shouldReturnEmptyListWhenNoActiveParticipants() {
        List<CareTeamParticipant> emptyList = new ArrayList<>();

        when(mockQuery.setParameter(anyString(), org.mockito.ArgumentMatchers.any())).thenReturn(mockQuery);
        when(mockQuery.list()).thenReturn(emptyList);

        List<CareTeamParticipant> result = hibernateCareTeamDAO.getActiveParticipants(new Date());

        assertEquals("Should return empty list", 0, result.size());
    }

    @Test
    public void shouldSaveParticipantSuccessfully() {
        // FIX: Don't just assert what we set - verify it was saved via saveOrUpdate
        CareTeamParticipant participant = createParticipant(1, false);

        CareTeamParticipant saved = hibernateCareTeamDAO.saveParticipant(participant);

        assertNotNull("Saved participant should not be null", saved);
        assertEquals("Saved participant ID should match", Integer.valueOf(1), saved.getId());
        // Verify sessionFactory.getCurrentSession().saveOrUpdate() was called
        verify(mockSession).saveOrUpdate(participant);
    }

    @Test
    public void shouldHandleMultipleParticipants() {
        List<CareTeamParticipant> participants = new ArrayList<>();
        for (int i = 1; i <= 5; i++) {
            participants.add(createParticipant(i, false));
        }

        when(mockQuery.setParameter(anyString(), org.mockito.ArgumentMatchers.any())).thenReturn(mockQuery);
        when(mockQuery.list()).thenReturn(participants);

        List<CareTeamParticipant> result = hibernateCareTeamDAO.getActiveParticipants(new Date());

        assertEquals(5, result.size());
    }

    private CareTeam createCareTeam() {
        CareTeam careTeam = new CareTeam();
        return careTeam;
    }

    private CareTeamParticipant createParticipant(Integer id, boolean voided) {
        CareTeamParticipant participant = new CareTeamParticipant();
        participant.setId(id);
        participant.setVoided(voided);
        Provider provider = mock(Provider.class);
        participant.setProvider(provider);
        Date now = new Date();
        participant.setStartTime(now);
        participant.setEndTime(new Date(now.getTime() + 3600000));
        return participant;
    }
}
