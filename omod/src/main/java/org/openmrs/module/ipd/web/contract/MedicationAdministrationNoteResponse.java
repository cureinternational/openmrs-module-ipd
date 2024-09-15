package org.openmrs.module.ipd.web.contract;

import lombok.*;
import org.codehaus.jackson.annotate.JsonIgnoreProperties;
import org.openmrs.module.ipd.api.model.MedicationAdministrationNote;
import org.openmrs.module.webservices.rest.web.ConversionUtil;
import org.openmrs.module.webservices.rest.web.representation.Representation;

import java.util.Date;

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

    public static MedicationAdministrationNoteResponse createFrom(MedicationAdministrationNote openmrsObject) {
        return MedicationAdministrationNoteResponse.builder()
                .uuid(openmrsObject.getUuid())
                .author(ConversionUtil.convertToRepresentation(openmrsObject.getAuthor(), Representation.REF))
                .recordedTime(openmrsObject.getRecordedTime())
                .text(openmrsObject.getText())
                .build();
    }
}
