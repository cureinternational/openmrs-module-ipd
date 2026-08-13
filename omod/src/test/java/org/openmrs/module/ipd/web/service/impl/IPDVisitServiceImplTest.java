package org.openmrs.module.ipd.web.service.impl;

import org.bahmni.module.bahmnicore.service.BahmniDrugOrderService;
import org.bahmni.module.bahmnicore.service.BahmniObsService;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.openmrs.Patient;
import org.openmrs.Visit;
import org.openmrs.VisitType;
import org.openmrs.api.ConceptService;
import org.openmrs.api.VisitService;
import org.openmrs.module.ipd.api.service.ReferenceService;
import org.openmrs.module.ipd.api.service.SlotService;
import org.openmrs.module.ipd.web.service.IPDScheduleService;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class IPDVisitServiceImplTest {

    private static final long HOUR = 3600_000L;

    @InjectMocks
    private IPDVisitServiceImpl service;

    @Mock private BahmniDrugOrderService drugOrderService;
    @Mock private IPDScheduleService ipdScheduleService;
    @Mock private SlotTimeCreationService slotTimeCreationService;
    @Mock private BahmniObsService bahmniObsService;
    @Mock private ConceptService conceptService;
    @Mock private ReferenceService referenceService;
    @Mock private VisitService visitService;
    @Mock private SlotService slotService;

    private Patient patient;

    @Before
    public void setUp() {
        patient = new Patient();
        patient.setUuid("patient-uuid");
    }

    @Test
    public void shouldIncludeInAbsentiaVisit_WhenItPrecedesCurrentVisit() throws Exception {
        Visit currentVisit = visit("current-uuid", "IPD", now(), null);
        Visit inAbsentiaVisit = visit("in-absentia-uuid", "In Absentia", now() - HOUR, null);

        when(visitService.getVisitsByPatient(patient)).thenReturn(Arrays.asList(inAbsentiaVisit, currentVisit));

        assertEquals(Arrays.asList("in-absentia-uuid"), getPrecedingVisitUuids("current-uuid"));
    }

    @Test
    public void shouldIncludeLabVisit_WhenItPrecedesCurrentVisit() throws Exception {
        Visit currentVisit = visit("current-uuid", "IPD", now(), null);
        Visit labVisit = visit("lab-visit-uuid", "LAB VISIT", now() - HOUR, null);

        when(visitService.getVisitsByPatient(patient)).thenReturn(Arrays.asList(labVisit, currentVisit));

        assertEquals(Arrays.asList("lab-visit-uuid"), getPrecedingVisitUuids("current-uuid"));
    }

    @Test
    public void shouldIncludeOPDVisit_WhenItPrecedesCurrentVisit() throws Exception {
        Visit currentVisit = visit("current-uuid", "IPD", now(), null);
        Visit opdVisit = visit("opd-uuid", "OPD", now() - HOUR, null);

        when(visitService.getVisitsByPatient(patient)).thenReturn(Arrays.asList(opdVisit, currentVisit));

        assertEquals(Arrays.asList("opd-uuid"), getPrecedingVisitUuids("current-uuid"));
    }

    @Test
    public void shouldStopCollecting_AfterFirstClosedIPDVisit() throws Exception {
        Visit currentVisit = visit("current-uuid", "IPD", now(), null);
        Visit opdVisit = visit("opd-uuid", "OPD", now() - HOUR, null);
        Visit closedIPDVisit = visit("closed-ipd-uuid", "IPD", now() - 2 * HOUR, now() - HOUR);
        Visit labVisitAfterClosedIPD = visit("lab-visit-uuid", "LAB VISIT", now() - 3 * HOUR, null);

        when(visitService.getVisitsByPatient(patient)).thenReturn(
                Arrays.asList(labVisitAfterClosedIPD, closedIPDVisit, opdVisit, currentVisit));

        assertEquals(Arrays.asList("opd-uuid", "closed-ipd-uuid"), getPrecedingVisitUuids("current-uuid"));
    }

    @Test
    public void shouldSkipVisitTypes_NotInOutpatientList() throws Exception {
        Visit currentVisit = visit("current-uuid", "IPD", now(), null);
        Visit otherVisit = visit("other-uuid", "OTHER", now() - HOUR, null);
        Visit opdVisit = visit("opd-uuid", "OPD", now() - 2 * HOUR, null);

        when(visitService.getVisitsByPatient(patient)).thenReturn(Arrays.asList(opdVisit, otherVisit, currentVisit));

        assertEquals(Arrays.asList("opd-uuid"), getPrecedingVisitUuids("current-uuid"));
    }

    @Test
    public void shouldReturnEmpty_WhenCurrentVisitNotFound() throws Exception {
        Visit visit = visit("some-uuid", "OPD", now(), null);

        when(visitService.getVisitsByPatient(patient)).thenReturn(Arrays.asList(visit));

        assertEquals(Collections.emptyList(), getPrecedingVisitUuids("unknown-current-uuid"));
    }

    @Test
    public void shouldReturnEmpty_WhenNoPrecedingVisits() throws Exception {
        Visit currentVisit = visit("current-uuid", "IPD", now(), null);

        when(visitService.getVisitsByPatient(patient)).thenReturn(Arrays.asList(currentVisit));

        assertEquals(Collections.emptyList(), getPrecedingVisitUuids("current-uuid"));
    }

    @Test
    public void shouldReturnEmptyList_WhenVisitUuidDoesNotExist() {
        when(visitService.getVisitByUuid("unknown-visit-uuid")).thenReturn(null);

        assertEquals(Collections.emptyList(), service.getPrescribedOrders("unknown-visit-uuid", true, null, null, null, true));

        verify(drugOrderService, never()).getPrescribedDrugOrders(any(), any(), any(), any(), any(), any(), any());
    }

    private Visit visit(String uuid, String visitTypeName, long startTime, Long stopTime) {
        Visit visit = new Visit();
        visit.setUuid(uuid);
        visit.setVisitType(new VisitType(visitTypeName, ""));
        visit.setStartDatetime(new Date(startTime));
        if (stopTime != null) {
            visit.setStopDatetime(new Date(stopTime));
        }
        return visit;
    }

    private long now() {
        return System.currentTimeMillis();
    }

    @SuppressWarnings("unchecked")
    private List<String> getPrecedingVisitUuids(String currentVisitUuid) throws Exception {
        Method method = IPDVisitServiceImpl.class.getDeclaredMethod("getPrecedingVisitUuids", Patient.class, String.class);
        method.setAccessible(true);
        return (List<String>) method.invoke(service, patient, currentVisitUuid);
    }
}
