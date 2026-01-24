package org.openmrs.module.ipd.web.contract;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.codehaus.jackson.annotate.JsonIgnoreProperties;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties
public class MedicationAdministrationAcknowledgementRequest {
    private String action; // "ACKNOWLEDGE"
    private String remarks; // Optional remarks from the acknowledger
}
