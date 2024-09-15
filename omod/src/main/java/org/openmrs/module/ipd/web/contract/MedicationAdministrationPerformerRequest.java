package org.openmrs.module.ipd.web.contract;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.codehaus.jackson.annotate.JsonIgnoreProperties;

import java.util.Date;
import java.util.concurrent.TimeUnit;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties
public class MedicationAdministrationPerformerRequest {
    private String uuid;
    private String providerUuid;
    private String function;
}
