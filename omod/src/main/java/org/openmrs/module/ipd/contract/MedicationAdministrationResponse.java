package org.openmrs.module.ipd.contract;

import lombok.*;
import org.openmrs.api.context.Context;
import org.openmrs.module.ipd.api.model.MedicationAdministrationNote;
import org.openmrs.module.ipd.api.model.MedicationAdministrationPerformer;
import org.openmrs.module.webservices.rest.web.ConversionUtil;
import org.openmrs.module.webservices.rest.web.representation.Representation;


import java.util.Date;
import java.util.List;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
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
        List<MedicationAdministrationNoteResponse> notes = new java.util.ArrayList<>();
        if (openmrsMedicationAdministration.getNotes() != null) {
            for (MedicationAdministrationNote note : openmrsMedicationAdministration.getNotes()) {
                notes.add(MedicationAdministrationNoteResponse.createFrom(note));
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
}

