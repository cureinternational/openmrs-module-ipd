package org.openmrs.module.ipd.model;

import lombok.*;
import org.openmrs.Provider;
import org.openmrs.module.bahmniemrapi.drugorder.contract.BahmniDrugOrder;
import org.openmrs.module.emrapi.encounter.domain.EncounterTransaction.DrugOrder;

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
