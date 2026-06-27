package org.openmrs.module.ipd.api.scheduler.tasks;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.MockitoAnnotations;
import org.openmrs.api.context.Context;
import org.openmrs.module.ipd.api.service.CareTeamService;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(PowerMockRunner.class)
@PrepareForTest(Context.class)
public class UnbookmarkPatientsAtShiftEndTest {

    private CareTeamService careTeamService;
    private UnbookmarkPatientsAtShiftEnd task;

    @Before
    public void setup() {
        MockitoAnnotations.initMocks(this);
        careTeamService = mock(CareTeamService.class);
        task = new UnbookmarkPatientsAtShiftEnd();

        PowerMockito.mockStatic(Context.class);
        when(Context.getService(CareTeamService.class)).thenReturn(careTeamService);
    }

    @Test
    public void shouldCallUnbookmarkServiceWhenTaskExecutes() {
        when(careTeamService.unbookmarkAllActivePatients()).thenReturn(5);

        task.execute();

        verify(careTeamService, times(1)).unbookmarkAllActivePatients();
    }

    @Test
    public void shouldHandleZeroParticipantsUnbookmarked() {
        when(careTeamService.unbookmarkAllActivePatients()).thenReturn(0);

        task.execute();

        verify(careTeamService, times(1)).unbookmarkAllActivePatients();
    }

    @Test
    public void shouldHandleMultipleParticipantsUnbookmarked() {
        when(careTeamService.unbookmarkAllActivePatients()).thenReturn(15);

        task.execute();

        verify(careTeamService, times(1)).unbookmarkAllActivePatients();
    }

    @Test
    public void shouldBeIdempotent() {
        when(careTeamService.unbookmarkAllActivePatients()).thenReturn(5);

        task.execute();
        task.execute();
        task.execute();

        verify(careTeamService, times(3)).unbookmarkAllActivePatients();
    }

    @Test
    public void shouldHandleExceptionDuringExecution() {
        when(Context.getAdministrationService()).thenThrow(new RuntimeException("Service error"));

        // Should not throw, should handle gracefully
        task.execute();

        verify(careTeamService, times(0)).unbookmarkAllActivePatients();
    }

    @Test
    public void shouldHandleNullCareTeamService() {
        when(Context.getService(CareTeamService.class)).thenReturn(null);

        // Should not throw, should handle gracefully
        task.execute();
    }
}
