package org.openmrs.module.ipd.api.model;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.openmrs.BaseChangeableOpenmrsData;
import org.openmrs.Concept;
import org.openmrs.Patient;
import org.openmrs.Visit;

import javax.persistence.*;
import java.util.Date;
import java.util.Set;

@Data
@NoArgsConstructor
@EqualsAndHashCode(onlyExplicitlyIncluded = true, callSuper = false)
@Entity
@Table(name = "care_team")
public class CareTeam extends BaseChangeableOpenmrsData {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "care_team_id")
    private Integer careTeamId;

    /**
     * FHIR:subject
     * Patient for Whom care team is for
     */
    @ManyToOne(optional = true)
    @JoinColumn(name = "patient_id")
    private Patient patient;

    @OneToOne
    @JoinColumn(name = "visit_id", referencedColumnName = "visit_id")
    private Visit visit;

    /**
     * FHIR:period.start
     * Starting time with inclusive boundary
     */
    @Column(name = "start_time")
    private Date startTime;

    /**
     * FHIR:period.end
     * Ending time with inclusive boundary, if not ongoing
     */
    @Column(name = "end_time")
    private Date endTime;

    /**
     * FHIR:performer
     * @see <a href="https://build.fhir.org/careteam-definitions.html#CareTeam.participant">
     *     	https://build.fhir.org/careteam-definitions.html#CareTeam.participant
     *     </a>Identifies all people and organizations who are expected to be involved in the care team.
     */
    @OneToMany(cascade = CascadeType.ALL)
    @JoinColumn(name = "care_team_id")
    private Set<CareTeamParticipant> participants;


    @Override
    public Integer getId() {
        return this.careTeamId;
    }

    @Override
    public void setId(Integer careTeamId) {
        this.careTeamId=careTeamId;
    }

    public Patient getPatient() {
        return patient;
    }

    public void setPatient(Patient patient) {
        this.patient = patient;
    }

    public Visit getVisit() {
        return visit;
    }

    public void setVisit(Visit visit) {
        this.visit = visit;
    }

    public Date getStartTime() {
        return startTime;
    }

    public void setStartTime(Date startTime) {
        this.startTime = startTime;
    }

    public Date getEndTime() {
        return endTime;
    }

    public void setEndTime(Date endTime) {
        this.endTime = endTime;
    }

    public Set<CareTeamParticipant> getParticipants() {
        return participants;
    }

    public void setParticipants(Set<CareTeamParticipant> participants) {
        this.participants = participants;
    }

    public void addParticipant(CareTeamParticipant participant) {
        if (this.participants == null) {
            this.participants = new java.util.HashSet<>();
        }
        this.participants.add(participant);
    }
}
