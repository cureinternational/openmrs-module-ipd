package org.openmrs.module.ipd.api.model;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.openmrs.BaseChangeableOpenmrsData;
import org.openmrs.Order;
import org.openmrs.Location;
import org.openmrs.Concept;

import javax.persistence.*;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@EqualsAndHashCode(onlyExplicitlyIncluded = true, callSuper = false)
@Entity
@Table(name = "ipd_slot")
public class Slot extends BaseChangeableOpenmrsData {
	
	private static final long serialVersionUID = 1L;
	
	public enum SlotStatus {
		SCHEDULED,
		NOT_DONE,
		COMPLETED,
		STOPPED,
		MISSED
	}
	
	@EqualsAndHashCode.Include
	@Id
	@GeneratedValue(strategy = GenerationType.AUTO)
	@Column(name = "slot_id")
	private Integer id;
	
	/**
	 * The location Where schedule occurs
	 */
	@ManyToOne
	@JoinColumn(name = "location_id", referencedColumnName = "location_id")
	private Location location; // bed location for patient
	
	/**
	 * The Service Type of the Schedule
	 */
	@OneToOne
	@JoinColumn(name = "service_type_id", referencedColumnName = "concept_id", nullable = false)
	private Concept serviceType; // as per schedule service type
	
	/**
	 * The entity that belongs to a Schedule
	 */
	@ManyToOne
	@JoinColumn(name = "schedule_id", referencedColumnName = "schedule_id", nullable = false)
	private Schedule schedule;

	/**
	 * Order with respect to the Slot
	 */
	@OneToOne
	@JoinColumn(name = "order_id", referencedColumnName = "order_id")
	private Order order;
	
	/**
	 * The Start Date the Slot
	 */
	@Column(name = "start_date_time", nullable = false)
	private LocalDateTime startDateTime; // slot start time

	/**
	 * The End Date the Slot
	 */
	@Column(name = "end_date_time")
	private LocalDateTime endDateTime; // can be null for now

	/**
	 * The current status of the slot.
	 */
	@Column(name = "status", nullable = false)
	@Enumerated(EnumType.STRING)
	private SlotStatus status = SlotStatus.SCHEDULED;

	/**
	 * The reference of medication administration if the medication is administered
	 */
	@OneToOne
	@JoinColumn(name = "medication_administration_id", referencedColumnName = "medication_administration_id")
	private MedicationAdministration medicationAdministration;

	@Column(name = "comments")
	private String notes;

	@Column(name = "variable_dosage_sequence")
	private Integer variableDosageSequence;

	public Boolean isStopped() {
		return this.status !=null && this.status == SlotStatus.STOPPED;
	}
}


