package org.openmrs.module.ipd.web.contract;

import com.fasterxml.jackson.annotation.JsonProperty;
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
public class CrossingSlotDTO {

    private Long epoch;

    @JsonProperty("isRecurringAcrossDays")
    private Boolean isRecurringAcrossDays;

    @JsonProperty("originDoseBucket")
    private Slot.SourceBucket originDoseBucket;
}
