package org.openmrs.module.ipd.web.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Result of computing the slot start times for a drug order: the flat list of times to
 * create slots for, plus a lookup of which of those times are midnight-crossing slots
 * (and how). {@link org.openmrs.module.ipd.web.factory.SlotFactory} tags each created
 * {@link org.openmrs.module.ipd.api.model.Slot} using this lookup.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SlotTimeCreationResult {
    private List<LocalDateTime> slotsStartTime;
    private Map<LocalDateTime, CrossingSlotTag> crossingTagsByStartTime;

    public static SlotTimeCreationResult withoutCrossingTags(List<LocalDateTime> slotsStartTime) {
        return new SlotTimeCreationResult(slotsStartTime, Collections.emptyMap());
    }
}
