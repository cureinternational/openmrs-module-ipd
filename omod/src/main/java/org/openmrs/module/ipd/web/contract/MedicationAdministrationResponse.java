package org.openmrs.module.ipd.web.contract;

import lombok.*;
import lombok.extern.slf4j.Slf4j;
import org.openmrs.api.context.Context;
import org.openmrs.module.fhirExtension.model.Task;
import org.openmrs.module.fhirExtension.service.TaskService;
import org.openmrs.module.ipd.api.model.MedicationAdministrationNote;
import org.openmrs.module.ipd.api.model.MedicationAdministrationPerformer;
import org.openmrs.module.ipd.web.util.AcknowledgementTaskUtil;
import org.openmrs.module.webservices.rest.web.ConversionUtil;
import org.openmrs.module.webservices.rest.web.representation.Representation;

import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Slf4j
public class MedicationAdministrationResponse {

    private String uuid;
    private String patientUuid;
    private String encounterUuid;
    private String orderUuid;
    private List<MedicationAdministrationPerformerResponse> providers;
    private List<MedicationAdministrationNoteResponse> notes;
    private String status;
    private String statusReason;
    private Object drug;
    private String dosingInstructions;
    private Double dose;
    private Object doseUnits;
    private Object route;
    private Object site;
    private Date administeredDateTime;

    public static MedicationAdministrationResponse createFrom(org.openmrs.module.ipd.api.model.MedicationAdministration openmrsMedicationAdministration) {
        if (openmrsMedicationAdministration == null) {
            return null;
        }
        String status = openmrsMedicationAdministration.getStatus().toCode() != null ? openmrsMedicationAdministration.getStatus().toCode() : null;
        String statusReason = openmrsMedicationAdministration.getStatusReason() != null ? openmrsMedicationAdministration.getStatusReason().getDisplayString() : null;
        String patientUuid = openmrsMedicationAdministration.getPatient() != null ? openmrsMedicationAdministration.getPatient().getUuid() : null;
        String encounterUuid = openmrsMedicationAdministration.getEncounter() != null ? openmrsMedicationAdministration.getEncounter().getUuid() : null;
        String orderUuid = openmrsMedicationAdministration.getDrugOrder() != null ? openmrsMedicationAdministration.getDrugOrder().getUuid() : null;

        List<MedicationAdministrationPerformerResponse> providers = new java.util.ArrayList<>();
        if (openmrsMedicationAdministration.getPerformers() != null) {
            for (MedicationAdministrationPerformer performer : openmrsMedicationAdministration.getPerformers()) {
                providers.add(MedicationAdministrationPerformerResponse.createFrom(performer));
            }
        }

        Map<String, Task> acknowledgementTasksByNoteUuid = new HashMap<>();
        Set<String> noteUuids = new HashSet<>();
        if (openmrsMedicationAdministration.getNotes() != null) {
            for (MedicationAdministrationNote note : openmrsMedicationAdministration.getNotes()) {
                if (note.getUuid() != null) {
                    noteUuids.add(note.getUuid());
                }
            }
            if (!noteUuids.isEmpty()) {
                acknowledgementTasksByNoteUuid = getAcknowledgementTasksForNotes(noteUuids);
            }
        }

        List<MedicationAdministrationNoteResponse> notes = new java.util.ArrayList<>();
        if (openmrsMedicationAdministration.getNotes() != null) {
            for (MedicationAdministrationNote note : openmrsMedicationAdministration.getNotes()) {
                notes.add(MedicationAdministrationNoteResponse.createFrom(note, acknowledgementTasksByNoteUuid));
            }
        }
        return MedicationAdministrationResponse.builder()
                .uuid(openmrsMedicationAdministration.getUuid())
                .administeredDateTime(openmrsMedicationAdministration.getAdministeredDateTime())
                .status(status)
                .statusReason(statusReason)
                .patientUuid(patientUuid)
                .encounterUuid(encounterUuid)
                .orderUuid(orderUuid)
                .providers(providers)
                .notes(notes)
                .drug(ConversionUtil.convertToRepresentation(openmrsMedicationAdministration.getDrug(), Representation.REF))
                .dosingInstructions(openmrsMedicationAdministration.getDosingInstructions())
                .dose(openmrsMedicationAdministration.getDose())
                .doseUnits(ConversionUtil.convertToRepresentation(openmrsMedicationAdministration.getDoseUnits(), Representation.REF))
                .route(ConversionUtil.convertToRepresentation(openmrsMedicationAdministration.getRoute(), Representation.REF))
                .site(ConversionUtil.convertToRepresentation(openmrsMedicationAdministration.getSite(), Representation.REF))
                .build();
    }

    private static Map<String, Task> getAcknowledgementTasksForNotes(Set<String> noteUuids) {
        if (noteUuids == null || noteUuids.isEmpty()) {
            return new HashMap<>();
        }

        try {
            TaskService taskService = Context.getService(TaskService.class);
            if (taskService == null) {
                return new HashMap<>();
            }
            return AcknowledgementTaskUtil.mapCompletedTasksByNoteUuid(taskService, noteUuids);
        } catch (Exception e) {
            log.error("Failed to resolve acknowledgement tasks for medication administration notes", e);
            return new HashMap<>();
        }
    }
}

