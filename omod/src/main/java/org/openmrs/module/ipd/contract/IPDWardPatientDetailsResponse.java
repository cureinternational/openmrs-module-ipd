package org.openmrs.module.ipd.contract;

import lombok.Builder;
import lombok.Getter;
import org.openmrs.module.ipd.api.model.IPDWardPatientDetails;

import java.util.List;
import java.util.stream.Collectors;

@Getter
@Builder
public class IPDWardPatientDetailsResponse {

    private List<AdmittedPatientResponse> admittedPatients;
    private Long totalPatients;


    public static IPDWardPatientDetailsResponse createFrom(IPDWardPatientDetails ipdWardPatientDetails) {
        return IPDWardPatientDetailsResponse.builder().
                admittedPatients(ipdWardPatientDetails.getActivePatients().stream().map(AdmittedPatientResponse::createFrom).collect(Collectors.toList())).
                totalPatients(ipdWardPatientDetails.getIpdWardWardPatientsSummary().getTotalPatients()).
                build();
    }
}
