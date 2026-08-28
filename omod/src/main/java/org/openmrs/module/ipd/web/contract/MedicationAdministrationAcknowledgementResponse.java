package org.openmrs.module.ipd.web.contract;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.openmrs.Provider;
import org.openmrs.api.context.Context;
import org.openmrs.module.fhirExtension.model.Task;
import org.openmrs.module.webservices.rest.web.ConversionUtil;
import org.openmrs.module.webservices.rest.web.representation.Representation;

import java.util.Date;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MedicationAdministrationAcknowledgementResponse {
    private String taskUuid;
    private Object approvedBy;
    private String remarks;
    private Date acknowledgedTime;

    public static MedicationAdministrationAcknowledgementResponse createFrom(Task task) {
        if (task == null || task.getFhirTask() == null) {
            return null;
        }

        Object approvedByProvider = null;
        if (task.getFhirTask().getOwnerReference() != null &&
                task.getFhirTask().getOwnerReference().getTargetUuid() != null) {
            Provider provider = Context.getProviderService()
                    .getProviderByUuid(task.getFhirTask().getOwnerReference().getTargetUuid());
            if (provider != null) {
                approvedByProvider = ConversionUtil.convertToRepresentation(provider, Representation.REF);
            }
        }

        return MedicationAdministrationAcknowledgementResponse.builder()
                .taskUuid(task.getFhirTask().getUuid())
                .approvedBy(approvedByProvider)
                .remarks(task.getFhirTask().getComment())
                .acknowledgedTime(task.getFhirTask().getDateCreated())
                .build();
    }
}
