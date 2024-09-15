package org.openmrs.module.ipd.contract;

import lombok.*;
import org.codehaus.jackson.annotate.JsonIgnoreProperties;
import org.openmrs.module.ipd.api.model.MedicationAdministrationPerformer;
import org.openmrs.module.webservices.rest.web.ConversionUtil;
import org.openmrs.module.webservices.rest.web.representation.Representation;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties
public class MedicationAdministrationPerformerResponse {
    private String uuid;
    private Object provider;
    private String function;

    public static MedicationAdministrationPerformerResponse createFrom(MedicationAdministrationPerformer openmrsMedicationAdministrationPerformer) {
        String function = openmrsMedicationAdministrationPerformer.getFunction() != null ? openmrsMedicationAdministrationPerformer.getFunction().getDisplayString() : null;
        return MedicationAdministrationPerformerResponse.builder()
                .uuid(openmrsMedicationAdministrationPerformer.getUuid())
                .provider(ConversionUtil.convertToRepresentation(openmrsMedicationAdministrationPerformer.getActor(), Representation.REF))
                .function(function)
                .build();
    }
}
