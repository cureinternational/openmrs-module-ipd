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
import org.openmrs.module.ipd.api.model.CareTeamParticipant;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

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
        mockUser.setId(1);
        when(Context.getAuthenticatedUser()).thenReturn(mockUser);
    }

    @Test
    public void shouldUnbookmarkAllActivePatients() {
        CareTeamParticipant participant1 = createMockParticipant(1, false);
        CareTeamParticipant participant2 = createMockParticipant(2, false);
        CareTeamParticipant participant3 = createMockParticipant(3, false);

        List<CareTeamParticipant> activeParticipants = new ArrayList<>();
        activeParticipants.add(participant1);
        activeParticipants.add(participant2);
        activeParticipants.add(participant3);

        when(careTeamDAO.getActiveParticipants(any(Date.class))).thenReturn(activeParticipants);
        when(careTeamDAO.saveParticipant(any(CareTeamParticipant.class))).thenAnswer(invocation -> invocation.getArgument(0));

        int count = careTeamService.unbookmarkAllActivePatients();

        assertEquals(3, count);
        verify(careTeamDAO, times(1)).getActiveParticipants(any(Date.class));
        verify(careTeamDAO, times(3)).saveParticipant(any(CareTeamParticipant.class));

        for (CareTeamParticipant participant : activeParticipants) {
            assertTrue(participant.getVoided());
            assertNotNull(participant.getDateVoided());
            assertEquals("Automatically unbookmarked at shift end", participant.getVoidReason());
        }
    }

    @Test
    public void shouldReturnZeroWhenNoActiveParticipants() {
        List<CareTeamParticipant> emptyList = new ArrayList<>();
        when(careTeamDAO.getActiveParticipants(any(Date.class))).thenReturn(emptyList);

        int count = careTeamService.unbookmarkAllActivePatients();

        assertEquals(0, count);
        verify(careTeamDAO, times(1)).getActiveParticipants(any(Date.class));
        verify(careTeamDAO, times(0)).saveParticipant(any(CareTeamParticipant.class));
    }

    @Test
    public void shouldSetAuditFieldsCorrectly() {
        CareTeamParticipant participant = createMockParticipant(1, false);
        List<CareTeamParticipant> activeParticipants = new ArrayList<>();
        activeParticipants.add(participant);

        when(careTeamDAO.getActiveParticipants(any(Date.class))).thenReturn(activeParticipants);
        when(careTeamDAO.saveParticipant(any(CareTeamParticipant.class))).thenAnswer(invocation -> invocation.getArgument(0));

        careTeamService.unbookmarkAllActivePatients();

        assertTrue(participant.getVoided());
        assertNotNull(participant.getVoidedBy());
        assertNotNull(participant.getDateVoided());
        assertEquals("Automatically unbookmarked at shift end", participant.getVoidReason());
    }

    @Test
    public void shouldVoidMultipleParticipantsFromDifferentProviders() {
        CareTeamParticipant nurse1Patient1 = createMockParticipant(1, false);
        CareTeamParticipant nurse1Patient2 = createMockParticipant(2, false);
        CareTeamParticipant nurse2Patient3 = createMockParticipant(3, false);
        CareTeamParticipant nurse2Patient4 = createMockParticipant(4, false);

        List<CareTeamParticipant> activeParticipants = new ArrayList<>();
        activeParticipants.add(nurse1Patient1);
        activeParticipants.add(nurse1Patient2);
        activeParticipants.add(nurse2Patient3);
        activeParticipants.add(nurse2Patient4);

        when(careTeamDAO.getActiveParticipants(any(Date.class))).thenReturn(activeParticipants);
        when(careTeamDAO.saveParticipant(any(CareTeamParticipant.class))).thenAnswer(invocation -> invocation.getArgument(0));

        int count = careTeamService.unbookmarkAllActivePatients();

        assertEquals(4, count);
        for (CareTeamParticipant participant : activeParticipants) {
            assertTrue("Participant " + participant.getId() + " should be voided", participant.getVoided());
        }
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
