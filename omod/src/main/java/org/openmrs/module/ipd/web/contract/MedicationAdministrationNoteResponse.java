package org.openmrs.module.ipd.web.contract;

import lombok.*;
import org.codehaus.jackson.annotate.JsonIgnoreProperties;
import org.openmrs.module.fhirExtension.model.Task;
import org.openmrs.module.ipd.api.model.MedicationAdministrationNote;
import org.openmrs.module.webservices.rest.web.ConversionUtil;
import org.openmrs.module.webservices.rest.web.representation.Representation;

import java.util.Date;
import java.util.Map;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties
public class MedicationAdministrationNoteResponse {

    private String uuid;
    private Object author;
    private Date recordedTime;
    private String text;
    private String amendmentReason;
    private String previousNoteUuid;
    private MedicationAdministrationAcknowledgementResponse acknowledgement;

    public static MedicationAdministrationNoteResponse createFrom(MedicationAdministrationNote openmrsObject) {
        if (openmrsObject == null) {
            return null;
        }

       return createFrom(openmrsObject, null);
    }
    public static MedicationAdministrationNoteResponse createFrom(MedicationAdministrationNote openmrsObject,
                                                                   Map<String, Task> acknowledgementTasksByNoteUuid) {
        if (openmrsObject == null) {
            return null;
        }

        Task acknowledgementTask = acknowledgementTasksByNoteUuid != null
                ? acknowledgementTasksByNoteUuid.get(openmrsObject.getUuid())
                : null;
        MedicationAdministrationAcknowledgementResponse acknowledgement = acknowledgementTask != null ?
                MedicationAdministrationAcknowledgementResponse.createFrom(acknowledgementTask) : null;

        return MedicationAdministrationNoteResponse.builder()
                .uuid(openmrsObject.getUuid())
                .author(ConversionUtil.convertToRepresentation(openmrsObject.getAuthor(), Representation.REF))
                .recordedTime(openmrsObject.getRecordedTime())
                .text(openmrsObject.getText())
                .amendmentReason(openmrsObject.getAmendmentReason())
                .previousNoteUuid(openmrsObject.getPreviousNote() != null ?
                    openmrsObject.getPreviousNote().getUuid() : null)
                .acknowledgement(acknowledgement)
                .build();
    }
}
