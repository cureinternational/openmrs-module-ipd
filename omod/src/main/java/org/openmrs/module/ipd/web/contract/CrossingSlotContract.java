package org.openmrs.module.ipd.web.contract;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.openmrs.module.ipd.api.model.Slot;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CrossingSlotContract {

    private Long epoch;
    private Boolean recurring;
    private Slot.SourceBucket sourceBucket;
}
