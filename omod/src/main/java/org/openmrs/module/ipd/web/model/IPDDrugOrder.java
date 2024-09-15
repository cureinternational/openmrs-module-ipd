package org.openmrs.module.ipd.web.model;

import lombok.*;
import org.openmrs.module.bahmniemrapi.drugorder.contract.BahmniDrugOrder;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IPDDrugOrder {

    private BahmniDrugOrder bahmniDrugOrder;
    private DrugOrderSchedule drugOrderSchedule;

    public static IPDDrugOrder createFrom(BahmniDrugOrder bahmniDrugOrder,DrugOrderSchedule drugOrderSchedule){
        return IPDDrugOrder.builder().
                bahmniDrugOrder(bahmniDrugOrder).
                drugOrderSchedule(drugOrderSchedule).
                build();
    }
}
