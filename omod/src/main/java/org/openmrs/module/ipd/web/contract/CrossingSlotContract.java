package org.openmrs.module.ipd.web.contract;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CrossingSlotContract {

    public enum SourceBucket {
        FIRST_DAY,
        DAY_WISE,
        FINAL
    }

    private Long epoch;
    private Boolean recurring;
    private SourceBucket sourceBucket;
}
