package org.openmrs.module.ipd.web.contract;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.codehaus.jackson.annotate.JsonIgnoreProperties;

import java.util.Date;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties
public class MedicationAdministrationAcknowledgementResponse {
    private String uuid;
    private String actionType;
    private Date actionDatetime;
    private String acknowledgedBy; // Provider name
    private String acknowledgedByUuid; // Provider UUID
    private String remarks;
}
