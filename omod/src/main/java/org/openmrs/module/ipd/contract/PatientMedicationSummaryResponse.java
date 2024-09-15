package org.openmrs.module.ipd.contract;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.openmrs.module.ipd.model.PatientMedicationSummary;

import java.util.List;
import java.util.stream.Collectors;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PatientMedicationSummaryResponse {

    private String patientUuid;
    private List<PrescribedOrderSlotSummaryResponse> prescribedOrderSlots;
    private List<MedicationSlotResponse> emergencyMedicationSlots;

    public static PatientMedicationSummaryResponse createFrom(PatientMedicationSummary patientMedicationSummary) {
        List<PrescribedOrderSlotSummaryResponse> prescribedOrderSlots = patientMedicationSummary.getPrescribedOrderSlots() != null
                ? patientMedicationSummary.getPrescribedOrderSlots().stream().map(PrescribedOrderSlotSummaryResponse::createFrom).collect(Collectors.toList())
                : null;
        List<MedicationSlotResponse> emergencyMedicationSlots = patientMedicationSummary.getEmergencyMedicationSlots() != null
                ? patientMedicationSummary.getEmergencyMedicationSlots().stream().map(MedicationSlotResponse::createFrom).collect(Collectors.toList())
                : null;
        return PatientMedicationSummaryResponse.builder()
                .patientUuid(patientMedicationSummary.getPatientUuid())
                .prescribedOrderSlots(prescribedOrderSlots)
                .emergencyMedicationSlots(emergencyMedicationSlots)
                .build();
    }
}
