package org.openmrs.module.ipd.api.service;

import org.junit.Test;
import org.openmrs.Location;
import org.openmrs.Patient;
import org.openmrs.PatientIdentifier;
import org.openmrs.PatientIdentifierType;
import org.openmrs.Person;
import org.openmrs.PersonName;
import org.openmrs.Provider;
import org.openmrs.Visit;
import org.openmrs.VisitType;
import org.openmrs.api.LocationService;
import org.openmrs.api.PatientService;
import org.openmrs.api.PersonService;
import org.openmrs.api.ProviderService;
import org.openmrs.module.ipd.api.BaseIntegrationTest;
import org.openmrs.module.ipd.api.model.CareTeam;
import org.openmrs.module.ipd.api.model.CareTeamParticipant;
import org.springframework.beans.factory.annotation.Autowired;
import java.util.Date;

import static org.junit.Assert.*;

public class CareTeamServiceIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private CareTeamService careTeamService;

    @Autowired
    private ProviderService providerService;

    @Autowired
    private PersonService personService;

    @Autowired
    private org.openmrs.api.VisitService visitService;

    @Autowired
    private PatientService patientService;

    @Autowired
    private LocationService locationService;

    @Test
    public void shouldUnbookmarkAllActivePatients() {
        // Setup: create care team with active participants
        Visit visit = createTestVisit();
        visitService.saveVisit(visit);
        CareTeam careTeam = createTestCareTeam(visit, 2);
        careTeamService.saveCareTeam(careTeam);

        // Verify initial state
        for (CareTeamParticipant p : careTeam.getParticipants()) {
            assertFalse("Participant should not be voided initially", p.getVoided());
        }

        // Execute unbooking
        int unbookmarked = careTeamService.unbookmarkAllActivePatients();

        // Assert actual count, not trivial >= 0
        assertEquals("Should unbookmark 2 participants", 2, unbookmarked);
    }

    @Test
    public void shouldProperlyVoidParticipantsWithCorrectAuditFields() {
        Visit visit = createTestVisit();
        visitService.saveVisit(visit);
        CareTeam careTeam = createTestCareTeam(visit, 1);
        careTeamService.saveCareTeam(careTeam);

        careTeamService.unbookmarkAllActivePatients();

        CareTeam reloaded = careTeamService.getCareTeamByVisit(visit);
        CareTeamParticipant participant = reloaded.getParticipants().iterator().next();

        // Assert real post-conditions
        assertTrue("Participant voided flag should be true", participant.getVoided());
        assertNotNull("VoidedBy audit field must be set", participant.getVoidedBy());
        assertNotNull("DateVoided audit field must be set", participant.getDateVoided());
        assertEquals("VoidReason must match", "Automatically unbookmarked at shift end", participant.getVoidReason());
    }

    @Test
    public void shouldHandleEmptyPatientList() {
        // Execute on clean database
        int count = careTeamService.unbookmarkAllActivePatients();

        // Assert exact zero count, not trivial >= 0
        assertEquals("Should unbookmark exactly 0 when no participants exist", 0, count);
    }

    private Visit createTestVisit() {
        // Create a test location first
        Location location = locationService.getLocation(1);
        if (location == null) {
            location = new Location();
            location.setName("Test Location");
            location = locationService.saveLocation(location);
        }

        // Create a test patient (Patient extends Person)
        Patient patient = new Patient();
        PersonName personName = new PersonName();
        personName.setGivenName("Test");
        personName.setFamilyName("Patient");
        patient.addName(personName);
        patient.setGender("F");

        // Add patient identifier
        PatientIdentifierType idType = patientService.getPatientIdentifierType(4);
        if (idType != null) {
            PatientIdentifier identifier = new PatientIdentifier();
            identifier.setIdentifierType(idType);
            identifier.setIdentifier("12345-4");
            identifier.setLocation(location);
            identifier.setPreferred(true);
            patient.addIdentifier(identifier);
        } else {
            // Fallback: try type 1
            idType = patientService.getPatientIdentifierType(1);
            if (idType != null) {
                PatientIdentifier identifier = new PatientIdentifier();
                identifier.setIdentifierType(idType);
                identifier.setIdentifier("12345-K");
                identifier.setLocation(location);
                identifier.setPreferred(true);
                patient.addIdentifier(identifier);
            }
        }

        Patient savedPatient = patientService.savePatient(patient);

        // Create visit with visit type
        Visit visit = new Visit();
        visit.setPatient(savedPatient);
        visit.setLocation(location);
        visit.setStartDatetime(new Date());
        // Set visit type (use type 1 or get the first available type)
        VisitType visitType = visitService.getVisitType(1);
        if (visitType == null) {
            // If type 1 doesn't exist, try to get any visit type
            visitType = visitService.getAllVisitTypes().get(0);
        }
        visit.setVisitType(visitType);
        return visit;
    }

    private CareTeam createTestCareTeam(Visit visit, int numParticipants) {
        CareTeam careTeam = new CareTeam();
        careTeam.setVisit(visit);

        for (int i = 0; i < numParticipants; i++) {
            CareTeamParticipant participant = new CareTeamParticipant();
            // Create test person and provider
            Person person = new Person();
            PersonName personName = new PersonName();
            personName.setGivenName("Test");
            personName.setFamilyName("Provider" + i);
            person.addName(personName);
            person.setGender("M");
            Person savedPerson = personService.savePerson(person);

            Provider provider = new Provider();
            String identifier = "test_provider_" + i + "_" + System.currentTimeMillis();
            provider.setIdentifier(identifier);
            provider.setPerson(savedPerson);
            Provider savedProvider = providerService.saveProvider(provider);

            participant.setProvider(savedProvider);
            participant.setStartTime(new Date(System.currentTimeMillis() - 3600000));
            participant.setEndTime(new Date(System.currentTimeMillis() + 7200000));
            participant.setVoided(false);
            careTeam.addParticipant(participant);
        }

        return careTeam;
    }
}
