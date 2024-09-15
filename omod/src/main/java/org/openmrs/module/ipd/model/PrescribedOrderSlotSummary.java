package org.openmrs.module.ipd.model;

import lombok.*;
import org.openmrs.module.ipd.api.model.Slot;

import java.util.List;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PrescribedOrderSlotSummary {

    private String orderUuid;
    private List<Slot> currentSlots;
    private Slot previousSlot;
    private Long initialSlotStartTime;
    private Long finalSlotStartTime;
}
