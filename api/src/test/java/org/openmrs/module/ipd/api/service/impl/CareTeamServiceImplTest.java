package org.openmrs.module.ipd.api.service.impl;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.openmrs.Provider;
import org.openmrs.User;
import org.openmrs.api.context.Context;
import org.openmrs.module.ipd.api.dao.CareTeamDAO;
import org.openmrs.module.ipd.api.model.CareTeam;
import org.openmrs.module.ipd.api.model.CareTeamParticipant;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(PowerMockRunner.class)
@PrepareForTest(Context.class)
public class CareTeamServiceImplTest {

    @Mock
    private CareTeamDAO careTeamDAO;

    @InjectMocks
    private CareTeamServiceImpl careTeamService;

    @Before
    public void setup() {
        MockitoAnnotations.initMocks(this);
        PowerMockito.mockStatic(Context.class);
        User mockUser = mock(User.class);
        // FIX: Use when() instead of setId() which is a no-op on mocks
        when(mockUser.getId()).thenReturn(1);
        when(Context.getAuthenticatedUser()).thenReturn(mockUser);
    }

    @Test
    public void shouldUnbookmarkAllActivePatients() {
        // FIX: Use getAllCareTeams() which is what the actual implementation calls
        CareTeam careTeam1 = createMockCareTeam(1);
        Set<CareTeamParticipant> participants = new HashSet<>();
        participants.add(createMockParticipant(1, false));
        participants.add(createMockParticipant(2, false));
        participants.add(createMockParticipant(3, false));
        careTeam1.setParticipants(participants);

        List<CareTeam> allTeams = new ArrayList<>();
        allTeams.add(careTeam1);

        when(careTeamDAO.getAllCareTeams()).thenReturn(allTeams);
        when(careTeamDAO.saveCareTeam(any(CareTeam.class))).thenAnswer(invocation -> invocation.getArgument(0));

        int count = careTeamService.unbookmarkAllActivePatients();

        assertEquals("Should unbookmark all 3 participants", 3, count);
        verify(careTeamDAO, times(1)).getAllCareTeams();
        // FIX: voidCareTeamParticipant is called 3 times, each saves the careTeam
        verify(careTeamDAO, times(3)).saveCareTeam(any(CareTeam.class));

        // FIX: Assert real post-conditions (voided + voidReason + auditBy)
        for (CareTeamParticipant participant : careTeam1.getParticipants()) {
            assertTrue("Participant " + participant.getId() + " should be voided", participant.getVoided());
            assertNotNull("Participant " + participant.getId() + " voidedBy must be set", participant.getVoidedBy());
            assertNotNull("Participant " + participant.getId() + " dateVoided must be set", participant.getDateVoided());
            assertEquals("Participant " + participant.getId() + " voidReason must match",
                "Automatically unbookmarked at shift end", participant.getVoidReason());
        }
    }

    @Test
    public void shouldReturnZeroWhenNoCareTeams() {
        // FIX: Test with empty getAllCareTeams() result
        when(careTeamDAO.getAllCareTeams()).thenReturn(new ArrayList<>());

        int count = careTeamService.unbookmarkAllActivePatients();

        assertEquals("Should return 0 when no care teams exist", 0, count);
        verify(careTeamDAO, times(1)).getAllCareTeams();
        verify(careTeamDAO, times(0)).saveCareTeam(any(CareTeam.class));
    }

    @Test
    public void shouldSetAuditFieldsCorrectly() {
        CareTeam careTeam = createMockCareTeam(1);
        CareTeamParticipant participant = createMockParticipant(1, false);
        careTeam.addParticipant(participant);

        when(careTeamDAO.getAllCareTeams()).thenReturn(new ArrayList<CareTeam>() {{ add(careTeam); }});
        when(careTeamDAO.saveCareTeam(any(CareTeam.class))).thenAnswer(invocation -> invocation.getArgument(0));

        careTeamService.unbookmarkAllActivePatients();

        // FIX: Assert ALL audit fields including voidedBy
        assertTrue("Participant voided flag should be true", participant.getVoided());
        assertNotNull("VoidedBy audit field must be set", participant.getVoidedBy());
        assertNotNull("DateVoided audit field must be set", participant.getDateVoided());
        assertEquals("VoidReason must match exact message",
            "Automatically unbookmarked at shift end", participant.getVoidReason());
    }

    @Test
    public void shouldVoidMultipleParticipantsAcrossMultipleCareTeams() {
        // FIX: Test with multiple care teams (not just one long list)
        CareTeam careTeam1 = createMockCareTeam(1);
        Set<CareTeamParticipant> team1Participants = new HashSet<>();
        team1Participants.add(createMockParticipant(1, false));
        team1Participants.add(createMockParticipant(2, false));
        careTeam1.setParticipants(team1Participants);

        CareTeam careTeam2 = createMockCareTeam(2);
        Set<CareTeamParticipant> team2Participants = new HashSet<>();
        team2Participants.add(createMockParticipant(3, false));
        team2Participants.add(createMockParticipant(4, false));
        careTeam2.setParticipants(team2Participants);

        List<CareTeam> allTeams = new ArrayList<>();
        allTeams.add(careTeam1);
        allTeams.add(careTeam2);

        when(careTeamDAO.getAllCareTeams()).thenReturn(allTeams);
        when(careTeamDAO.saveCareTeam(any(CareTeam.class))).thenAnswer(invocation -> invocation.getArgument(0));

        int count = careTeamService.unbookmarkAllActivePatients();

        assertEquals("Should unbookmark 4 participants from 2 care teams", 4, count);

        // FIX: Assert voided state on all participants across all teams
        for (CareTeam team : allTeams) {
            for (CareTeamParticipant p : team.getParticipants()) {
                assertTrue("Participant " + p.getId() + " should be voided", p.getVoided());
                assertEquals("Participant " + p.getId() + " voidReason must match",
                    "Automatically unbookmarked at shift end", p.getVoidReason());
            }
        }
    }

    private CareTeam createMockCareTeam(Integer id) {
        CareTeam careTeam = new CareTeam();
        careTeam.setId(id);
        return careTeam;
    }

    private CareTeamParticipant createMockParticipant(Integer id, boolean voided) {
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
