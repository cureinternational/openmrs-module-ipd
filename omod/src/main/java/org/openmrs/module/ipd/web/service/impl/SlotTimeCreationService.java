package org.openmrs.module.ipd.web.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.openmrs.DrugOrder;
import org.openmrs.api.impl.BaseOpenmrsService;
import org.openmrs.module.ipd.api.util.DateTimeUtil;
import org.openmrs.module.ipd.web.model.DrugOrderSchedule;
import org.openmrs.module.ipd.web.model.StageScheduleStatus;
import org.openmrs.module.ipd.api.model.Slot;
import org.openmrs.module.ipd.web.contract.ScheduleMedicationRequest;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

import static org.openmrs.module.ipd.web.contract.ScheduleMedicationRequest.MedicationFrequency.FIXED_SCHEDULE_FREQUENCY;
import static org.openmrs.module.ipd.web.contract.ScheduleMedicationRequest.MedicationFrequency.START_TIME_DURATION_FREQUENCY;

@Slf4j
@Service
@Component
public class SlotTimeCreationService extends BaseOpenmrsService {

    public static final List<String> START_TIME_FREQUENCIES = Arrays.asList("Every Hour", "Every 2 hours", "Every 3 hours", "Every 4 hours", "Every 6 hours", "Every 8 hours", "Every 12 hours", "Once a day", "Nocte (At Night)", "Every 30 minutes", "STAT (Immediately)", "In Afternoon", "In Morning", "Once a week", "Twice a week", "Three times a week", "Four days a week", "Five days a week", "Six days a week", "On alternate days", "Monthly", "Once a month", "Every 2 weeks", "Every 3 weeks");

    private static final ObjectMapper MAPPER = new ObjectMapper();

    public List<LocalDateTime> createSlotsStartTimeFrom(ScheduleMedicationRequest request, DrugOrder order) {
        if (request.getSlotStartTimeAsLocaltime() != null && request.getMedicationFrequency() == START_TIME_DURATION_FREQUENCY) {
            return getSlotsStartTimeWithStartTimeDurationFrequency(request, order);
        } else if ((!CollectionUtils.isEmpty(request.getFirstDaySlotsStartTimeAsLocalTime()) ||
                !CollectionUtils.isEmpty(request.getDayWiseSlotsStartTimeAsLocalTime()) ||
                !CollectionUtils.isEmpty(request.getRemainingDaySlotsStartTimeAsLocalTime()))
                        && request.getMedicationFrequency() == FIXED_SCHEDULE_FREQUENCY) {
            return getSlotsStartTimeWithFixedScheduleFrequency(request, order);
        }

        return Collections.emptyList();
    }

    private List<LocalDateTime> getSlotsStartTimeWithFixedScheduleFrequency(ScheduleMedicationRequest request, DrugOrder order) {
        int numberOfSlotsStartTimeToBeCreated = order.getFrequency() == null && request.getVariableDosageSequence() != null
            ? computeVdpNumberOfSlots(order, request.getVariableDosageSequence())
            : (int) (Math.ceil(order.getQuantity() / order.getDose()));

        List<LocalDateTime> slotsStartTime = new ArrayList<>();
        if (!CollectionUtils.isEmpty(request.getFirstDaySlotsStartTimeAsLocalTime())) {
            List<LocalDateTime> slotsToBeAddedForFirstDay = numberOfSlotsStartTimeToBeCreated < request.getFirstDaySlotsStartTimeAsLocalTime().size()
                ? request.getFirstDaySlotsStartTimeAsLocalTime().subList(0, numberOfSlotsStartTimeToBeCreated)
                : request.getFirstDaySlotsStartTimeAsLocalTime();

            slotsStartTime.addAll(slotsToBeAddedForFirstDay);
            numberOfSlotsStartTimeToBeCreated -= slotsToBeAddedForFirstDay.size();
        }


        List<LocalDateTime> remainingDaySlotsStartTime = request.getRemainingDaySlotsStartTimeAsLocalTime();
        if (!CollectionUtils.isEmpty(remainingDaySlotsStartTime) && numberOfSlotsStartTimeToBeCreated > 0) {
            List<LocalDateTime> slotsToBeAddedForRemainingDay = numberOfSlotsStartTimeToBeCreated < remainingDaySlotsStartTime.size()
                    ? remainingDaySlotsStartTime.subList(0, numberOfSlotsStartTimeToBeCreated)
                    : remainingDaySlotsStartTime;
            numberOfSlotsStartTimeToBeCreated -= slotsToBeAddedForRemainingDay.size();
            slotsStartTime.addAll(slotsToBeAddedForRemainingDay);
        }

        List<LocalDateTime> nextSlotsStartTime = request.getDayWiseSlotsStartTimeAsLocalTime();
        if (!CollectionUtils.isEmpty(nextSlotsStartTime) && numberOfSlotsStartTimeToBeCreated > 0) {
            List<LocalDateTime> initialSlotsToBeAddedForSecondDay = numberOfSlotsStartTimeToBeCreated < nextSlotsStartTime.size()
                    ? nextSlotsStartTime.subList(0, numberOfSlotsStartTimeToBeCreated)
                    : nextSlotsStartTime;
            slotsStartTime.addAll(initialSlotsToBeAddedForSecondDay);
            numberOfSlotsStartTimeToBeCreated -= initialSlotsToBeAddedForSecondDay.size();
            while (numberOfSlotsStartTimeToBeCreated > 0) {
                nextSlotsStartTime = nextSlotsStartTime.stream().map(slotStartTime -> slotStartTime.plusHours(24)).collect(Collectors.toList());
                if (numberOfSlotsStartTimeToBeCreated >= nextSlotsStartTime.size()) {
                    slotsStartTime.addAll(nextSlotsStartTime);
                    numberOfSlotsStartTimeToBeCreated -= nextSlotsStartTime.size();
                } else {
                    slotsStartTime.addAll(nextSlotsStartTime.subList(0, numberOfSlotsStartTimeToBeCreated));
                    numberOfSlotsStartTimeToBeCreated -= nextSlotsStartTime.subList(0, numberOfSlotsStartTimeToBeCreated).size();
                }
            }
        }

        return slotsStartTime;
    }

    private List<LocalDateTime> getSlotsStartTimeWithStartTimeDurationFrequency(ScheduleMedicationRequest request, DrugOrder order) {
        int numberOfSlotsStartTimeToBeCreated = order.getFrequency() == null && request.getVariableDosageSequence() != null
            ? computeVdpNumberOfSlots(order, request.getVariableDosageSequence())
            : (order.getQuantity() == 0.0 || order.getFrequency() == null || order.getDuration() == null) ? 1 : (int) (Math.ceil(order.getQuantity() / order.getDose()));
        List<LocalDateTime> slotsStartTime = new ArrayList<>();
        Double slotDurationInHours = order.getFrequency() != null
            ? 24 / order.getFrequency().getFrequencyPerDay()
            : (request.getVariableDosageSequence() != null
                ? 24.0 / getFrequencyPerDayFromFhir(order, request.getVariableDosageSequence())
                : 0);
        LocalDateTime slotStartTime = request.getSlotStartTimeAsLocaltime();
        while (numberOfSlotsStartTimeToBeCreated-- > 0) {
            slotsStartTime.add(slotStartTime);
            if(slotDurationInHours.compareTo(1.0) >= 0)
            {
                slotStartTime = slotStartTime.plusHours(slotDurationInHours.longValue());
            }
            else {
                Double minutesToBeAdded = 60 * slotDurationInHours;
                slotStartTime = slotStartTime.plusMinutes(minutesToBeAdded.longValue());
            }
        }
        return slotsStartTime;
    }

    public HashMap<String , DrugOrderSchedule> getDrugOrderScheduledTime(Map<DrugOrder,List<Slot>> slotsByOrder){
        HashMap<String, DrugOrderSchedule> drugOrderScheduleHash= new HashMap<>();
        for (DrugOrder drugOrder : slotsByOrder.keySet()) {
            DrugOrderSchedule drugOrderSchedule = new DrugOrderSchedule();
            if (drugOrder.getAsNeeded() || drugOrder.getFrequency() == null || drugOrder.getDuration() == null || drugOrder.getQuantity() == 0.0) {
                drugOrderSchedule.setSlotStartTime(DateTimeUtil.convertLocalDateTimeToUTCEpoc(slotsByOrder.get(drugOrder).get(0).getStartDateTime()));
            }
            else {
                Double frequencyPerDay = drugOrder.getFrequency().getFrequencyPerDay();
                String frequency = drugOrder.getFrequency().getName();
                Map<LocalDate, List<LocalDateTime>> groupedByDateAndEpoch = slotsByOrder.get(drugOrder).stream()
                        .collect(Collectors.groupingBy(
                                obj -> obj.getStartDateTime().toLocalDate(),
                                Collectors.mapping(
                                        obj -> obj.getStartDateTime(),
                                        Collectors.toList()
                                )
                        ));

                List<List<LocalDateTime>> sortedList = groupedByDateAndEpoch.entrySet().stream()
                        .sorted(Map.Entry.comparingByKey()) // Sort by LocalDate in ascending order
                        .map(Map.Entry::getValue) // Get the list of Longs for each entry
                        .collect(Collectors.toList()); // Collect the list of lists into a single ArrayList

                if (START_TIME_FREQUENCIES.contains(frequency)) {
                    drugOrderSchedule.setSlotStartTime(DateTimeUtil.convertLocalDateTimeToUTCEpoc(sortedList.get(0).get(0)));
                } else if (sortedList.get(0).size() == frequencyPerDay || (sortedList.size() == 1)) {
                    drugOrderSchedule.setDayWiseSlotsStartTime(sortedList.get(0).stream().map(DateTimeUtil::convertLocalDateTimeToUTCEpoc).collect(Collectors.toList()));
                } else {
                    drugOrderSchedule.setFirstDaySlotsStartTime(sortedList.get(0).stream().map(DateTimeUtil::convertLocalDateTimeToUTCEpoc).collect(Collectors.toList()));
                    drugOrderSchedule.setRemainingDaySlotsStartTime(sortedList.get(sortedList.size() - 1).stream().map(DateTimeUtil::convertLocalDateTimeToUTCEpoc).collect(Collectors.toList()));
                    if (sortedList.size() > 2) {
                        drugOrderSchedule.setDayWiseSlotsStartTime(sortedList.get(1).stream().map(DateTimeUtil::convertLocalDateTimeToUTCEpoc).collect(Collectors.toList()));
                    }
                }
            }
            List<Slot> slots = slotsByOrder.get(drugOrder);
            drugOrderSchedule.setSlots(slots);
            drugOrderSchedule.setStageSchedules(buildStageSchedules(slots));
            drugOrderScheduleHash.put(drugOrder.getUuid(),drugOrderSchedule);
        }
        return drugOrderScheduleHash;
    }

    public List<StageScheduleStatus> buildStageSchedules(List<Slot> slots) {
        if (slots == null || slots.isEmpty()) return Collections.emptyList();

        Map<Integer, List<Slot>> bySequence = slots.stream()
            .filter(s -> s.getVariableDosageSequence() != null)
            .collect(Collectors.groupingBy(Slot::getVariableDosageSequence));

        if (bySequence.isEmpty()) return Collections.emptyList();

        return bySequence.entrySet().stream()
            .map(e -> buildStageScheduleStatus(e.getValue(), e.getKey()))
            .filter(Objects::nonNull)
            .collect(Collectors.toList());
    }

    private StageScheduleStatus buildStageScheduleStatus(List<Slot> stageSlots, Integer sequence) {
        if (stageSlots.isEmpty()) return null;

        boolean isStartTimeFrequency = isStartTimeFrequencyForStage(stageSlots.get(0), sequence);

        StageScheduleStatus.StageScheduleStatusBuilder builder = StageScheduleStatus.builder()
            .variableDosageSequence(sequence)
            .isScheduled(true)
            .administrationStarted(stageSlots.stream().anyMatch(s -> s.getMedicationAdministration() != null))
            .allAttended(stageSlots.stream().noneMatch(s -> s.getStatus().equals(Slot.SlotStatus.SCHEDULED)))
            .pendingSlotsAvailable(stageSlots.stream().anyMatch(s ->
                s.getStartDateTime() != null &&
                LocalDateTime.now().isBefore(s.getStartDateTime()) &&
                s.getStatus().equals(Slot.SlotStatus.SCHEDULED)))
            .notes(stageSlots.get(0).getNotes());

        if (isStartTimeFrequency) {
            builder.slotStartTime(stageSlots.stream()
                .filter(s -> s.getStartDateTime() != null)
                .min(Comparator.comparing(Slot::getStartDateTime))
                .map(s -> DateTimeUtil.convertLocalDateTimeToUTCEpoc(s.getStartDateTime()))
                .orElse(null));
        } else {
            populateDayWiseSlotTimes(stageSlots, builder);
        }

        return builder.build();
    }

    private void populateDayWiseSlotTimes(List<Slot> stageSlots, StageScheduleStatus.StageScheduleStatusBuilder builder) {
        List<List<LocalDateTime>> sortedDaySlots = groupSlotsByDay(stageSlots);
        if (sortedDaySlots.isEmpty()) return;

        if (sortedDaySlots.size() == 1 || sortedDaySlots.get(0).size() == sortedDaySlots.get(1).size()) {
            builder.dayWiseSlotsStartTime(toEpochList(sortedDaySlots.get(0)));
        } else {
            builder.firstDaySlotsStartTime(toEpochList(sortedDaySlots.get(0)));
            builder.remainingDaySlotsStartTime(toEpochList(sortedDaySlots.get(sortedDaySlots.size() - 1)));
            if (sortedDaySlots.size() > 2) {
                builder.dayWiseSlotsStartTime(toEpochList(sortedDaySlots.get(1)));
            }
        }
    }

    private List<List<LocalDateTime>> groupSlotsByDay(List<Slot> stageSlots) {
        TreeMap<LocalDate, List<LocalDateTime>> slotsByDate = stageSlots.stream()
            .filter(s -> s.getStartDateTime() != null)
            .collect(Collectors.groupingBy(
                s -> s.getStartDateTime().toLocalDate(),
                TreeMap::new,
                Collectors.mapping(Slot::getStartDateTime, Collectors.toList())
            ));
        return new ArrayList<>(slotsByDate.values());
    }

    private List<Long> toEpochList(List<LocalDateTime> dateTimes) {
        return dateTimes.stream()
            .map(DateTimeUtil::convertLocalDateTimeToUTCEpoc)
            .collect(Collectors.toList());
    }

    private int computeVdpNumberOfSlots(DrugOrder order, Integer sequence) {
        try {
            JsonNode dosages = MAPPER.readTree(order.getDosingInstructions());
            for (JsonNode dosage : dosages) {
                if (dosage.path("sequence").asInt() != sequence) continue;
                for (JsonNode ext : dosage.path("extension")) {
                    if ("isLoadingDose".equals(ext.path("url").asText()) && ext.path("valueBoolean").asBoolean(false)) {
                        return 1;
                    }
                }
                double duration = dosage.path("timing").path("repeat").path("duration").asDouble(0);
                String durationUnit = dosage.path("timing").path("repeat").path("durationUnit").asText("d");
                double durationDays = normalizeFhirDurationToDays(duration, durationUnit);
                String frequencyName = dosage.path("timing").path("code").path("text").asText(null);
                double frequencyPerDay = getFrequencyPerDayByName(frequencyName);
                return (int) Math.ceil(durationDays * frequencyPerDay);
            }
        } catch (Exception e) {
            log.warn("Failed to compute VDP numberOfSlots from FHIR for order {} sequence {}", order.getUuid(), sequence, e);
        }
        return 1;
    }

    private double getFrequencyPerDayFromFhir(DrugOrder order, Integer sequence) {
        try {
            JsonNode dosages = MAPPER.readTree(order.getDosingInstructions());
            for (JsonNode dosage : dosages) {
                if (dosage.path("sequence").asInt() != sequence) continue;
                String frequencyName = dosage.path("timing").path("code").path("text").asText(null);
                return getFrequencyPerDayByName(frequencyName);
            }
        } catch (Exception e) {
            log.warn("Failed to get frequencyPerDay from FHIR for order {} sequence {}", order.getUuid(), sequence, e);
        }
        return 1.0;
    }

    private double getFrequencyPerDayByName(String frequencyName) {
        if (frequencyName == null) return 1.0;
        return org.openmrs.api.context.Context.getOrderService().getOrderFrequencies(false)
            .stream()
            .filter(f -> frequencyName.equals(f.getConcept().getName().getName()))
            .findFirst()
            .map(f -> f.getFrequencyPerDay())
            .orElse(1.0);
    }

    private double normalizeFhirDurationToDays(double duration, String durationUnit) {
        switch (durationUnit != null ? durationUnit : "d") {
            case "wk": return duration * 7;
            case "mo": return duration * 30;
            default:   return duration;
        }
    }

    private boolean isStartTimeFrequencyForStage(Slot slot, Integer sequence) {
        try {
            DrugOrder drugOrder = (DrugOrder) slot.getOrder();
            String dosingInstructions = drugOrder.getDosingInstructions();
            if (dosingInstructions == null) return true;

            JsonNode dosages = MAPPER.readTree(dosingInstructions);
            if (!dosages.isArray()) return true;

            for (JsonNode dosage : dosages) {
                if (dosage.path("sequence").asInt() != sequence) continue;

                // Loading dose is always start-time regardless of frequency name
                JsonNode extensions = dosage.path("extension");
                for (JsonNode ext : extensions) {
                    if ("isLoadingDose".equals(ext.path("url").asText()) && ext.path("valueBoolean").asBoolean(false)) {
                        return true;
                    }
                }

                String frequencyName = dosage.path("timing").path("code").path("text").asText(null);
                return START_TIME_FREQUENCIES.contains(frequencyName);
            }
        } catch (Exception e) {
            log.warn("Failed to determine scheduling mode for slot order {}",
                slot.getOrder() != null ? slot.getOrder().getUuid() : "null", e);
        }
        return true;
    }
}
