package org.openmrs.module.ipd.api.service.impl;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.openmrs.Location;
import org.openmrs.Provider;
import org.openmrs.api.LocationService;
import org.openmrs.api.ProviderService;
import org.openmrs.api.context.Context;
import org.openmrs.module.ipd.api.dao.WardDAO;
import org.openmrs.module.ipd.api.model.AdmittedPatient;
import org.openmrs.module.ipd.api.model.WardPatientsSummary;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PowerMockIgnore;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.core.classloader.annotations.SuppressStaticInitializationFor;
import org.powermock.modules.junit4.PowerMockRunner;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

@RunWith(PowerMockRunner.class)
@PrepareForTest({Context.class})
@SuppressStaticInitializationFor({"org.openmrs.api.context.Context", "org.openmrs.Provider"})
@PowerMockIgnore({"javax.management.*", "javax.xml.*", "org.xml.sax.*", "org.w3c.dom.*", "com.sun.*", "sun.*"})
public class WardServiceImplTest {

    @InjectMocks
    private WardServiceImpl wardService;

    @Mock
    private WardDAO wardDAO;

    @Mock
    private LocationService locationService;

    @Mock
    private ProviderService providerService;

    @Before
    public void setUp() {
        PowerMockito.mockStatic(Context.class);
        Mockito.when(Context.getService(LocationService.class)).thenReturn(locationService);
        Mockito.when(Context.getProviderService()).thenReturn(providerService);
    }

    @Test
    public void shouldGetWardPatientSummaryGivenWardUuidAndProviderUuid() {
        String wardUuid = "ward-uuid-1";
        String providerUuid = "provider-uuid-1";

        Location location = new Location();
        location.setUuid(wardUuid);

        Provider provider = new Provider();
        provider.setUuid(providerUuid);

        WardPatientsSummary expectedSummary = new WardPatientsSummary(5L, 3L);

        Mockito.when(locationService.getLocationByUuid(wardUuid)).thenReturn(location);
        Mockito.when(providerService.getProviderByUuid(providerUuid)).thenReturn(provider);
        Mockito.when(wardDAO.getWardPatientSummary(Mockito.eq(location), Mockito.eq(provider), Mockito.any()))
                .thenReturn(expectedSummary);

        WardPatientsSummary result = wardService.getIPDWardPatientSummary(wardUuid, providerUuid);

        Assert.assertNotNull(result);
        Assert.assertEquals(5L, result.getTotalPatients().longValue());
        Assert.assertEquals(3L, result.getTotalProviderPatients().longValue());
        Mockito.verify(locationService, Mockito.times(1)).getLocationByUuid(wardUuid);
        Mockito.verify(providerService, Mockito.times(1)).getProviderByUuid(providerUuid);
        Mockito.verify(wardDAO, Mockito.times(1))
                .getWardPatientSummary(Mockito.eq(location), Mockito.eq(provider), Mockito.any());
    }

    @Test
    public void shouldGetWardPatientsByUuidGivenWardUuid() {
        String wardUuid = "ward-uuid-1";

        Location location = new Location();
        location.setUuid(wardUuid);

        List<AdmittedPatient> expectedPatients = Collections.emptyList();

        Mockito.when(locationService.getLocationByUuid(wardUuid)).thenReturn(location);
        Mockito.when(wardDAO.getAdmittedPatients(location, null, null, null)).thenReturn(expectedPatients);

        List<AdmittedPatient> result = wardService.getWardPatientsByUuid(wardUuid, null);

        Assert.assertNotNull(result);
        Mockito.verify(locationService, Mockito.times(1)).getLocationByUuid(wardUuid);
        Mockito.verify(wardDAO, Mockito.times(1)).getAdmittedPatients(location, null, null, null);
    }

    @Test
    public void shouldGetWardPatientsByUuidWithSortByGivenWardUuid() {
        String wardUuid = "ward-uuid-1";
        String sortBy = "bedNumber";

        Location location = new Location();
        location.setUuid(wardUuid);

        List<AdmittedPatient> expectedPatients = Collections.emptyList();

        Mockito.when(locationService.getLocationByUuid(wardUuid)).thenReturn(location);
        Mockito.when(wardDAO.getAdmittedPatients(location, null, null, sortBy)).thenReturn(expectedPatients);

        List<AdmittedPatient> result = wardService.getWardPatientsByUuid(wardUuid, sortBy);

        Assert.assertNotNull(result);
        Mockito.verify(wardDAO, Mockito.times(1)).getAdmittedPatients(location, null, null, sortBy);
    }

    @Test
    public void shouldGetPatientsByWardAndProviderGivenWardUuidAndProviderUuid() {
        String wardUuid = "ward-uuid-1";
        String providerUuid = "provider-uuid-1";
        String sortBy = "admissionDate";

        Location location = new Location();
        location.setUuid(wardUuid);

        Provider provider = new Provider();
        provider.setUuid(providerUuid);

        List<AdmittedPatient> expectedPatients = Collections.emptyList();

        Mockito.when(locationService.getLocationByUuid(wardUuid)).thenReturn(location);
        Mockito.when(providerService.getProviderByUuid(providerUuid)).thenReturn(provider);
        Mockito.when(wardDAO.getAdmittedPatients(Mockito.eq(location), Mockito.eq(provider), Mockito.any(), Mockito.eq(sortBy)))
                .thenReturn(expectedPatients);

        List<AdmittedPatient> result = wardService.getPatientsByWardAndProvider(wardUuid, providerUuid, sortBy);

        Assert.assertNotNull(result);
        Mockito.verify(locationService, Mockito.times(1)).getLocationByUuid(wardUuid);
        Mockito.verify(providerService, Mockito.times(1)).getProviderByUuid(providerUuid);
        Mockito.verify(wardDAO, Mockito.times(1))
                .getAdmittedPatients(Mockito.eq(location), Mockito.eq(provider), Mockito.any(), Mockito.eq(sortBy));
    }

    @Test
    public void shouldSearchWardPatientsGivenWardUuidAndSearchCriteria() {
        String wardUuid = "ward-uuid-1";
        List<String> searchKeys = Arrays.asList("bedNumber", "patientName");
        String searchValue = "101";
        String sortBy = "bedNumber";

        Location location = new Location();
        location.setUuid(wardUuid);

        List<AdmittedPatient> expectedPatients = Collections.emptyList();

        Mockito.when(locationService.getLocationByUuid(wardUuid)).thenReturn(location);
        Mockito.when(wardDAO.searchAdmittedPatients(location, searchKeys, searchValue, sortBy))
                .thenReturn(expectedPatients);

        List<AdmittedPatient> result = wardService.searchWardPatients(wardUuid, searchKeys, searchValue, sortBy);

        Assert.assertNotNull(result);
        Mockito.verify(locationService, Mockito.times(1)).getLocationByUuid(wardUuid);
        Mockito.verify(wardDAO, Mockito.times(1)).searchAdmittedPatients(location, searchKeys, searchValue, sortBy);
    }

    @Test
    public void shouldGetAllAdmittedPatients() {
        List<AdmittedPatient> expectedPatients = Collections.emptyList();

        Mockito.when(wardDAO.getAdmittedPatients(null, null, null, null)).thenReturn(expectedPatients);

        List<AdmittedPatient> result = wardService.getAdmittedPatients();

        Assert.assertNotNull(result);
        Mockito.verify(wardDAO, Mockito.times(1)).getAdmittedPatients(null, null, null, null);
    }
}
