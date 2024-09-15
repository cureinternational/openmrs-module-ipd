package org.openmrs.module.ipd.contract;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IPDTreatmentsResponse {

    private List<IPDDrugOrderResponse> ipdDrugOrders;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private List<MedicationAdministrationResponse> emergencyMedications;

    public static IPDTreatmentsResponse createFrom(List<IPDDrugOrderResponse> ipdDrugOrders, List<MedicationAdministrationResponse> emergencyMedications) {
            return IPDTreatmentsResponse.builder()
                    .ipdDrugOrders(ipdDrugOrders)
                    .emergencyMedications(emergencyMedications)
                    .build();
    }
}
