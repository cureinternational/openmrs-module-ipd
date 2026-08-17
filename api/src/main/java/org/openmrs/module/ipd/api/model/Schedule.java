package org.openmrs.module.ipd.api.model;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.openmrs.BaseChangeableOpenmrsData;
import org.openmrs.Concept;
import org.openmrs.Visit;

import javax.persistence.*;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@EqualsAndHashCode(onlyExplicitlyIncluded = true, callSuper = false)
@Entity
@Table(name = "ipd_schedule")
public class Schedule extends BaseChangeableOpenmrsData {

	private static final long serialVersionUID = 1L;

	@EqualsAndHashCode.Include
	@Id
	@GeneratedValue(strategy = GenerationType.AUTO)
	@Column(name = "schedule_id")
	private Integer id;

	/**
	 * Should we rename it to "subject"
	 * This can have reference for which the schedule is being created
	 */
	@ManyToOne(cascade = CascadeType.ALL)
	@JoinColumn(name = "subject_reference_id", referencedColumnName = "reference_id", nullable = false)
	private Reference subject;

	/**
	 * Should we rename it to "carer/executor/  actor"
	 * This can have reference which execute the schedule is being created
	 */
	@ManyToOne(cascade = CascadeType.ALL)
	@JoinColumn(name = "actor_reference_id", referencedColumnName = "reference_id", nullable = false)
	private Reference actor;

	@Column(name = "active", nullable = false)
	private boolean active = Boolean.TRUE;

	@OneToOne
	@JoinColumn(name = "service_type_id", referencedColumnName = "concept_id", nullable = false)
	private Concept serviceType;

	@OneToOne
	@JoinColumn(name = "visit_id", referencedColumnName = "visit_id")
	private Visit visit;

	@Column(name = "start_date", nullable = false)
	private LocalDateTime startDate;

	@Column(name = "end_date")
	private LocalDateTime endDate;

	@Column(name = "comments")
	private String comments;

	@Column(name = "update_complete_schedule")
	private Boolean isUpdateCompleteSchedule;
}
