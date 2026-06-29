package org.openmrs.module.ipd.api.scheduler.tasks;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.MockitoAnnotations;
import org.openmrs.api.AdministrationService;
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
    private AdministrationService administrationService;
    private UnbookmarkPatientsAtShiftEnd task;
    private static final String SHIFT_DETAILS_JSON = "{\"1\": {\"shiftStartTime\":\"08:00\",\"shiftEndTime\":\"19:00\"},\"2\": {\"shiftStartTime\":\"19:00\",\"shiftEndTime\":\"08:00\"}}";

    @Before
    public void setup() {
        MockitoAnnotations.initMocks(this);
        careTeamService = mock(CareTeamService.class);
        administrationService = mock(AdministrationService.class);
        task = new UnbookmarkPatientsAtShiftEnd();

        PowerMockito.mockStatic(Context.class);
        when(Context.getService(CareTeamService.class)).thenReturn(careTeamService);
        when(Context.getAdministrationService()).thenReturn(administrationService);
        when(administrationService.getGlobalProperty("ipd.shiftDetails")).thenReturn(SHIFT_DETAILS_JSON);
    }

    @Test
    public void shouldParseShiftDetailsFromGlobalProperty() {
        task.execute();
        verify(administrationService).getGlobalProperty("ipd.shiftDetails");
    }

    @Test
    public void shouldHandleNullGlobalProperty() {
        when(administrationService.getGlobalProperty("ipd.shiftDetails")).thenReturn(null);

        task.execute();

        verify(careTeamService, times(0)).unbookmarkAllActivePatients();
    }

    @Test
    public void shouldHandleEmptyGlobalProperty() {
        when(administrationService.getGlobalProperty("ipd.shiftDetails")).thenReturn("");

        task.execute();

        verify(careTeamService, times(0)).unbookmarkAllActivePatients();
    }

    @Test
    public void shouldRescheduleTaskExecution() {
        task.execute();
        verify(administrationService).getGlobalProperty("ipd.shiftDetails");
    }
}
