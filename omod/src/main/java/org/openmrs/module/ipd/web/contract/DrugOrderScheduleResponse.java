package org.openmrs.module.ipd.web.contract;

import lombok.*;
import lombok.extern.slf4j.Slf4j;
import org.openmrs.module.ipd.api.model.Slot;
import org.openmrs.module.ipd.web.model.DrugOrderSchedule;
import org.openmrs.module.ipd.web.model.StageScheduleStatus;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Slf4j
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DrugOrderScheduleResponse {

    private static final com.fasterxml.jackson.databind.ObjectMapper MAPPER =
        new com.fasterxml.jackson.databind.ObjectMapper();

    private List<Long> firstDaySlotsStartTime;
    private List<Long> dayWiseSlotsStartTime;
    private List<Long> remainingDaySlotsStartTime;
    private Long slotStartTime;
    private Boolean medicationAdministrationStarted;
    private Boolean pendingSlotsAvailable;
    private Boolean allSlotsAttended;
    private String notes;
    private List<StageScheduleStatus> stageSchedules;

    public static DrugOrderScheduleResponse createFrom(DrugOrderSchedule drugOrderSchedule) {
        List<Slot> slots = drugOrderSchedule.getSlots();

        Map<Integer, List<Slot>> bySequence = slots.stream()
            .filter(s -> s.getVariableDosageSequence() != null)
            .collect(java.util.stream.Collectors.groupingBy(Slot::getVariableDosageSequence));

        List<StageScheduleStatus> stageSchedules = bySequence.entrySet().stream()
            .map(e -> {
                List<Slot> stageSlots = e.getValue();
                Integer sequence = e.getKey();

                Long stageSlotStartTime = null;
                List<Long> stageDayWise = null;
                List<Long> stageFirstDay = null;
                List<Long> stageRemainingDay = null;

                boolean isStartTimeFrequency = isStartTimeFrequencyForStage(stageSlots.get(0), sequence);

                if (isStartTimeFrequency) {
                    // Start-time mode: single slot per day, same as regular start-time orders
                    stageSlotStartTime = stageSlots.stream()
                        .min(java.util.Comparator.comparing(Slot::getStartDateTime))
                        .map(s -> org.openmrs.module.ipd.api.util.DateTimeUtil.convertLocalDateTimeToUTCEpoc(s.getStartDateTime()))
                        .orElse(null);
                } else {
                    // Fixed-schedule mode: multiple slots per day, same grouping as regular fixed-schedule orders
                    java.util.TreeMap<java.time.LocalDate, List<java.time.LocalDateTime>> slotsByDate =
                        stageSlots.stream().collect(java.util.stream.Collectors.groupingBy(
                            s -> s.getStartDateTime().toLocalDate(),
                            java.util.TreeMap::new,
                            java.util.stream.Collectors.mapping(Slot::getStartDateTime, java.util.stream.Collectors.toList())
                        ));
                    List<List<java.time.LocalDateTime>> sortedDaySlots = new java.util.ArrayList<>(slotsByDate.values());

                    if (sortedDaySlots.size() == 1 || sortedDaySlots.get(0).size() == sortedDaySlots.get(1).size()) {
                        stageDayWise = sortedDaySlots.get(0).stream()
                            .map(org.openmrs.module.ipd.api.util.DateTimeUtil::convertLocalDateTimeToUTCEpoc)
                            .collect(java.util.stream.Collectors.toList());
                    } else {
                        stageFirstDay = sortedDaySlots.get(0).stream()
                            .map(org.openmrs.module.ipd.api.util.DateTimeUtil::convertLocalDateTimeToUTCEpoc)
                            .collect(java.util.stream.Collectors.toList());
                        if (sortedDaySlots.size() > 1) {
                            stageDayWise = sortedDaySlots.get(1).stream()
                                .map(org.openmrs.module.ipd.api.util.DateTimeUtil::convertLocalDateTimeToUTCEpoc)
                                .collect(java.util.stream.Collectors.toList());
                        }
                        if (sortedDaySlots.size() > 2) {
                            stageRemainingDay = sortedDaySlots.get(sortedDaySlots.size() - 1).stream()
                                .map(org.openmrs.module.ipd.api.util.DateTimeUtil::convertLocalDateTimeToUTCEpoc)
                                .collect(java.util.stream.Collectors.toList());
                        }
                    }
                }

                return StageScheduleStatus.builder()
                    .variableDosageSequence(sequence)
                    .isScheduled(!stageSlots.isEmpty())
                    .administrationStarted(stageSlots.stream().anyMatch(s -> s.getMedicationAdministration() != null))
                    .allAttended(stageSlots.stream().noneMatch(s -> s.getStatus().equals(Slot.SlotStatus.SCHEDULED)))
                    .pendingSlotsAvailable(stageSlots.stream().anyMatch(s ->
                        java.time.LocalDateTime.now().isBefore(s.getStartDateTime()) &&
                        s.getStatus().equals(Slot.SlotStatus.SCHEDULED)))
                    .notes(stageSlots.get(0).getNotes())
                    .slotStartTime(stageSlotStartTime)
                    .firstDaySlotsStartTime(stageFirstDay)
                    .dayWiseSlotsStartTime(stageDayWise)
                    .remainingDaySlotsStartTime(stageRemainingDay)
                    .build();
            })
            .collect(java.util.stream.Collectors.toList());

        return DrugOrderScheduleResponse.builder()
            .firstDaySlotsStartTime(drugOrderSchedule.getFirstDaySlotsStartTime())
            .dayWiseSlotsStartTime(drugOrderSchedule.getDayWiseSlotsStartTime())
            .remainingDaySlotsStartTime(drugOrderSchedule.getRemainingDaySlotsStartTime())
            .slotStartTime(drugOrderSchedule.getSlotStartTime())
            .medicationAdministrationStarted(slots.stream().anyMatch(slot -> slot.getMedicationAdministration() != null))
            .allSlotsAttended(!(slots.stream().anyMatch(slot -> slot.getStatus().equals(Slot.SlotStatus.SCHEDULED))))
            .pendingSlotsAvailable(slots.stream().anyMatch(slot ->
                java.time.LocalDateTime.now().isBefore(slot.getStartDateTime()) &&
                slot.getStatus().equals(Slot.SlotStatus.SCHEDULED)))
            .notes(slots.isEmpty() ? null : slots.get(0).getNotes())
            .stageSchedules(stageSchedules)
            .build();
    }

    /**
     * Determines whether a VDP stage should use start-time scheduling mode
     * by reading the frequency name from the FHIR dosage JSON — the same
     * approach used for regular orders via START_TIME_FREQUENCIES.
     * Loading dose (isLoadingDose=true) is always start-time (single occurrence).
     */
    private static boolean isStartTimeFrequencyForStage(Slot slot, Integer sequence) {
        try {
            org.openmrs.DrugOrder drugOrder = (org.openmrs.DrugOrder) slot.getOrder();
            String dosingInstructions = drugOrder.getDosingInstructions();
            if (dosingInstructions == null) return true;

            com.fasterxml.jackson.databind.JsonNode dosages = MAPPER.readTree(dosingInstructions);
            if (!dosages.isArray()) return true;

            for (com.fasterxml.jackson.databind.JsonNode dosage : dosages) {
                if (dosage.path("sequence").asInt() != sequence) continue;

                // Loading dose is always start-time regardless of frequency name
                com.fasterxml.jackson.databind.JsonNode extensions = dosage.path("extension");
                for (com.fasterxml.jackson.databind.JsonNode ext : extensions) {
                    if ("isLoadingDose".equals(ext.path("url").asText()) && ext.path("valueBoolean").asBoolean(false)) {
                        return true;
                    }
                }

                String frequencyName = dosage.path("timing").path("code").path("text").asText(null);
                return org.openmrs.module.ipd.web.service.impl.SlotTimeCreationService.START_TIME_FREQUENCIES.contains(frequencyName);
            }
        } catch (Exception e) {
            log.warn("Failed to determine scheduling mode for slot order {}: {}",
                slot.getOrder() != null ? slot.getOrder().getUuid() : "null", e.getMessage());
        }
        return true;
    }
}
