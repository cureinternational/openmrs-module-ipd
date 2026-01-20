package org.openmrs.module.ipd.web.contract;

import lombok.*;
import org.openmrs.module.bahmniemrapi.drugorder.contract.BahmniOrderAttribute;
import org.openmrs.module.emrapi.encounter.domain.EncounterTransaction;
import org.openmrs.module.ipd.web.model.IPDDrugOrder;

import java.util.List;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IPDDrugOrderResponse {

    private EncounterTransaction.DrugOrder drugOrder;
    private EncounterTransaction.Provider provider;
    private DrugOrderScheduleResponse drugOrderSchedule;
    private List<BahmniOrderAttribute> drugOrderAttributes;


    public static IPDDrugOrderResponse createFrom(IPDDrugOrder ipdDrugOrder) {
        IPDDrugOrderResponse ipdDrugOrderResponse= IPDDrugOrderResponse.builder().
                drugOrder(ipdDrugOrder.getBahmniDrugOrder().getDrugOrder())
                .provider(ipdDrugOrder.getBahmniDrugOrder().getProvider())
                .drugOrderAttributes(ipdDrugOrder.getBahmniDrugOrder().getOrderAttributes())
                .build();
        if (ipdDrugOrder.getDrugOrderSchedule() != null){
            ipdDrugOrderResponse.setDrugOrderSchedule(DrugOrderScheduleResponse.createFrom(ipdDrugOrder.getDrugOrderSchedule()));
        }
        return ipdDrugOrderResponse;
    }

    public boolean equals(Object otherOrder) {
        if (otherOrder == null) {
            return false;
        } else if (!(otherOrder instanceof IPDDrugOrderResponse)) {
            return false;
        } else {
            return super.equals(otherOrder);
        }
    }


}