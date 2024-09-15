package org.openmrs.module.ipd.contract;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import org.openmrs.module.ipd.api.model.IPDPatientDetails;
import java.util.List;
import java.util.stream.Collectors;

@Builder
@Getter
@Setter
public class IPDPatientDetailsResponse {
    private List<AdmittedPatientResponse> admittedPatients;
    private Integer totalPatients;

    public static IPDPatientDetailsResponse createFrom(IPDPatientDetails ipdPatientDetails) {
        return IPDPatientDetailsResponse.builder()
                .admittedPatients(ipdPatientDetails.getAdmittedPatients().stream().map(AdmittedPatientResponse::createFrom).collect(Collectors.toList()))
                .totalPatients(ipdPatientDetails.getPatientCount())
                .build();
    }
}
