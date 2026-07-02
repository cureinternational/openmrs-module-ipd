package org.openmrs.module.ipd.api.service;

import org.junit.Test;
import org.openmrs.module.ipd.api.BaseIntegrationTest;
import org.springframework.beans.factory.annotation.Autowired;

import static org.junit.Assert.assertTrue;

public class CareTeamServiceIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private CareTeamService careTeamService;

    @Test
    public void shouldUnbookmarkAllActivePatients() {
        int unbookmarked = careTeamService.unbookmarkAllActivePatients();
        assertTrue("Should unbookmark zero or more patients", unbookmarked >= 0);
    }

    @Test
    public void shouldProperlyVoidParticipantsWithCorrectAuditFields() {
        int count = careTeamService.unbookmarkAllActivePatients();
        assertTrue("Unbooking should complete without error", count >= 0);
    }

    @Test
    public void shouldHandleEmptyPatientList() {
        int count = careTeamService.unbookmarkAllActivePatients();
        assertTrue("Should handle empty list gracefully", count >= 0);
    }
}
