package org.openmrs.module.ipd.model;

import lombok.*;
import org.openmrs.module.ipd.api.model.Slot;

import java.util.List;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PatientMedicationSummary {

    private String patientUuid;
    private List<PrescribedOrderSlotSummary> prescribedOrderSlots;
    private List<Slot> emergencyMedicationSlots;

}
