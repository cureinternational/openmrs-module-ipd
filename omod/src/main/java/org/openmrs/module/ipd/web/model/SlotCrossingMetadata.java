package org.openmrs.module.ipd.web.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.openmrs.module.ipd.api.model.Slot;

/**
 * Describes how a single generated slot time relates to a midnight-crossing schedule,
 * so that it can be tagged directly onto the {@link Slot} it produces instead of being
 * stored separately.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SlotCrossingMetadata {
    private Slot.SourceBucket originDoseBucket;
    private Boolean isRecurringAcrossDays;
}
