package org.openmrs.module.ipd.web.service.impl;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;
import org.openmrs.module.bedmanagement.entity.Bed;
import org.openmrs.module.bedmanagement.entity.BedPatientAssignment;
import org.openmrs.module.ipd.api.model.AdmittedPatient;
import org.openmrs.module.ipd.api.model.IPDPatientDetails;
import org.openmrs.module.ipd.api.model.WardPatientsSummary;
import org.openmrs.module.ipd.api.service.WardService;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

@RunWith(MockitoJUnitRunner.class)
public class IPDWardServiceImplTest {

    @InjectMocks
    private IPDWardServiceImpl ipdWardService;

    @Mock
    private WardService wardService;

    // -------- getIPDWardPatientSummary --------

    @Test
    public void shouldDelegateGetIPDWardPatientSummaryToWardService() {
        String wardUuid = "ward-uuid-1";
        String providerUuid = "provider-uuid-1";
        WardPatientsSummary expected = new WardPatientsSummary(10L, 4L);

        Mockito.when(wardService.getIPDWardPatientSummary(wardUuid, providerUuid)).thenReturn(expected);

        WardPatientsSummary result = ipdWardService.getIPDWardPatientSummary(wardUuid, providerUuid);

        Assert.assertEquals(expected, result);
        Mockito.verify(wardService, Mockito.times(1)).getIPDWardPatientSummary(wardUuid, providerUuid);
    }

    // -------- getIPDPatientByWard --------

    @Test
    public void shouldReturnEmptyDetailsWhenWardServiceReturnsNull() {
        String wardUuid = "ward-uuid-1";

        Mockito.when(wardService.getWardPatientsByUuid(wardUuid, null)).thenReturn(null);

        IPDPatientDetails result = ipdWardService.getIPDPatientByWard(wardUuid, 0, 10, null);

        Assert.assertNotNull(result);
        Assert.assertEquals(0, result.getAdmittedPatients().size());
        Assert.assertEquals(Integer.valueOf(0), result.getPatientCount());
    }

    @Test
    public void shouldReturnEmptyDetailsWhenWardServiceReturnsEmptyList() {
        String wardUuid = "ward-uuid-1";

        Mockito.when(wardService.getWardPatientsByUuid(wardUuid, null)).thenReturn(Collections.emptyList());

        IPDPatientDetails result = ipdWardService.getIPDPatientByWard(wardUuid, 0, 10, null);

        Assert.assertNotNull(result);
        Assert.assertEquals(0, result.getAdmittedPatients().size());
        Assert.assertEquals(Integer.valueOf(0), result.getPatientCount());
    }

    @Test
    public void shouldApplyPaginationWithOffsetAndLimit() {
        String wardUuid = "ward-uuid-1";
        List<AdmittedPatient> patients = Arrays.asList(
                createAdmittedPatient("1"),
                createAdmittedPatient("2"),
                createAdmittedPatient("3"),
                createAdmittedPatient("4"),
                createAdmittedPatient("5")
        );

        Mockito.when(wardService.getWardPatientsByUuid(wardUuid, null)).thenReturn(patients);

        IPDPatientDetails result = ipdWardService.getIPDPatientByWard(wardUuid, 1, 2, null);

        Assert.assertEquals(2, result.getAdmittedPatients().size());
        Assert.assertEquals(Integer.valueOf(5), result.getPatientCount());
    }

    @Test
    public void shouldClampOffsetToListSizeWhenOffsetExceedsTotalCount() {
        String wardUuid = "ward-uuid-1";
        List<AdmittedPatient> patients = Arrays.asList(
                createAdmittedPatient("1"),
                createAdmittedPatient("2")
        );

        Mockito.when(wardService.getWardPatientsByUuid(wardUuid, null)).thenReturn(patients);

        IPDPatientDetails result = ipdWardService.getIPDPatientByWard(wardUuid, 10, 5, null);

        Assert.assertEquals(0, result.getAdmittedPatients().size());
        Assert.assertEquals(Integer.valueOf(2), result.getPatientCount());
    }

    @Test
    public void shouldSortByBedNumberNumericallyWhenSortByIsBedNumber() {
        String wardUuid = "ward-uuid-1";
        List<AdmittedPatient> patients = Arrays.asList(
                createAdmittedPatient("10"),
                createAdmittedPatient("2"),
                createAdmittedPatient("1"),
                createAdmittedPatient("20")
        );

        Mockito.when(wardService.getWardPatientsByUuid(wardUuid, "bedNumber")).thenReturn(patients);

        IPDPatientDetails result = ipdWardService.getIPDPatientByWard(wardUuid, 0, 4, "bedNumber");

        Assert.assertEquals(4, result.getAdmittedPatients().size());
        Assert.assertEquals("1", result.getAdmittedPatients().get(0).getBedPatientAssignment().getBed().getBedNumber());
        Assert.assertEquals("2", result.getAdmittedPatients().get(1).getBedPatientAssignment().getBed().getBedNumber());
        Assert.assertEquals("10", result.getAdmittedPatients().get(2).getBedPatientAssignment().getBed().getBedNumber());
        Assert.assertEquals("20", result.getAdmittedPatients().get(3).getBedPatientAssignment().getBed().getBedNumber());
    }

    @Test
    public void shouldNotApplyNumericSortWhenBedNumbersContainNonNumericValues() {
        String wardUuid = "ward-uuid-1";
        List<AdmittedPatient> patients = Arrays.asList(
                createAdmittedPatient("B10"),
                createAdmittedPatient("A2"),
                createAdmittedPatient("1")
        );

        Mockito.when(wardService.getWardPatientsByUuid(wardUuid, "bedNumber")).thenReturn(patients);

        IPDPatientDetails result = ipdWardService.getIPDPatientByWard(wardUuid, 0, 3, "bedNumber");

        Assert.assertEquals(3, result.getAdmittedPatients().size());
        // Order unchanged when not all-numeric
        Assert.assertEquals("B10", result.getAdmittedPatients().get(0).getBedPatientAssignment().getBed().getBedNumber());
    }

    // -------- getIPDPatientsByWardAndProvider --------

    @Test
    public void shouldReturnEmptyDetailsWhenProviderPatientListIsNull() {
        String wardUuid = "ward-uuid-1";
        String providerUuid = "provider-uuid-1";

        Mockito.when(wardService.getPatientsByWardAndProvider(wardUuid, providerUuid, null)).thenReturn(null);

        IPDPatientDetails result = ipdWardService.getIPDPatientsByWardAndProvider(wardUuid, providerUuid, 0, 10, null);

        Assert.assertNotNull(result);
        Assert.assertEquals(0, result.getAdmittedPatients().size());
        Assert.assertEquals(Integer.valueOf(0), result.getPatientCount());
    }

    @Test
    public void shouldApplyPaginationForPatientsByWardAndProvider() {
        String wardUuid = "ward-uuid-1";
        String providerUuid = "provider-uuid-1";
        List<AdmittedPatient> patients = Arrays.asList(
                createAdmittedPatient("1"),
                createAdmittedPatient("2"),
                createAdmittedPatient("3")
        );

        Mockito.when(wardService.getPatientsByWardAndProvider(wardUuid, providerUuid, null)).thenReturn(patients);

        IPDPatientDetails result = ipdWardService.getIPDPatientsByWardAndProvider(wardUuid, providerUuid, 0, 2, null);

        Assert.assertEquals(2, result.getAdmittedPatients().size());
        Assert.assertEquals(Integer.valueOf(3), result.getPatientCount());
    }

    // -------- searchIPDPatientsInWard --------

    @Test
    public void shouldReturnEmptyDetailsWhenSearchReturnsNull() {
        String wardUuid = "ward-uuid-1";
        List<String> searchKeys = Arrays.asList("bedNumber");

        Mockito.when(wardService.searchWardPatients(wardUuid, searchKeys, "5", null)).thenReturn(null);

        IPDPatientDetails result = ipdWardService.searchIPDPatientsInWard(wardUuid, searchKeys, "5", 0, 10, null);

        Assert.assertNotNull(result);
        Assert.assertEquals(0, result.getAdmittedPatients().size());
        Assert.assertEquals(Integer.valueOf(0), result.getPatientCount());
    }

    @Test
    public void shouldApplyPaginationForSearchResults() {
        String wardUuid = "ward-uuid-1";
        List<String> searchKeys = Arrays.asList("patientName");
        List<AdmittedPatient> patients = Arrays.asList(
                createAdmittedPatient("5"),
                createAdmittedPatient("6"),
                createAdmittedPatient("7"),
                createAdmittedPatient("8")
        );

        Mockito.when(wardService.searchWardPatients(wardUuid, searchKeys, "John", null)).thenReturn(patients);

        IPDPatientDetails result = ipdWardService.searchIPDPatientsInWard(wardUuid, searchKeys, "John", 1, 2, null);

        Assert.assertEquals(2, result.getAdmittedPatients().size());
        Assert.assertEquals(Integer.valueOf(4), result.getPatientCount());
    }

    private AdmittedPatient createAdmittedPatient(String bedNumber) {
        Bed bed = Mockito.mock(Bed.class);
        Mockito.when(bed.getBedNumber()).thenReturn(bedNumber);

        BedPatientAssignment assignment = Mockito.mock(BedPatientAssignment.class);
        Mockito.when(assignment.getBed()).thenReturn(bed);

        return new AdmittedPatient(assignment, 0L, null);
    }
}
