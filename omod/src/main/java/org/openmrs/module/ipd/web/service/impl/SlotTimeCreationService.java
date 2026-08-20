package org.openmrs.module.ipd.web.service.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.openmrs.DrugOrder;
import org.openmrs.api.impl.BaseOpenmrsService;
import org.openmrs.module.ipd.api.util.DateTimeUtil;
import org.openmrs.module.ipd.web.model.SlotCrossingMetadata;
import org.openmrs.module.ipd.web.model.DrugOrderSchedule;
import org.openmrs.module.ipd.web.model.StageScheduleStatus;
import org.openmrs.module.ipd.web.model.SlotTimeCreationResult;
import org.openmrs.module.ipd.api.model.Slot;
import org.openmrs.module.ipd.web.contract.CrossingSlotDTO;
import org.openmrs.module.ipd.web.contract.ScheduleMedicationRequest;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

import static org.openmrs.module.ipd.web.contract.ScheduleMedicationRequest.MedicationFrequency.FIXED_SCHEDULE_FREQUENCY;
import static org.openmrs.module.ipd.web.contract.ScheduleMedicationRequest.MedicationFrequency.START_TIME_DURATION_FREQUENCY;

@Service
@Component
public class SlotTimeCreationService extends BaseOpenmrsService {

    private static final Log log = LogFactory.getLog(SlotTimeCreationService.class);
    private static final List<String> INTRADAY_DOSE_FIELDS = Arrays.asList(
            "morningDose",
            "afternoonDose",
            "eveningDose",
            "nightDose"
    );
    private final ObjectMapper objectMapper = new ObjectMapper();

    public static final List<String> START_TIME_FREQUENCIES= Arrays.asList(new String[]{"Every Hour", "Every 2 hours", "Every 3 hours", "Every 4 hours", "Every 6 hours", "Every 8 hours", "Every 12 hours", "Once a day", "Nocte (At Night)", "Every 30 minutes", "STAT (Immediately)", "In Afternoon", "In Morning", "Once a week", "Twice a week", "Three times a week", "Four days a week", "Five days a week", "Six days a week", "On alternate days", "Monthly", "Once a month", "Every 2 weeks", "Every 3 weeks"});

    /**
     * Main entry point for creating medication slots from frontend request.
     * Routes to appropriate slot creation logic based on medication frequency type.
     *
     * @param request Medication scheduling request containing slot times and frequency info
     * @param order The underlying drug order
     * @return SlotTimeCreationResult containing all slots and crossing slot metadata
     *
     * Routes:
     * - START_TIME_DURATION: Simple start time + duration calculation
     * - FIXED_SCHEDULE: Complex multi-day pattern with day-wise expansion and crossing slots
     */
    public SlotTimeCreationResult createSlotsStartTimeFrom(ScheduleMedicationRequest request, DrugOrder order) {
        if (request.getSlotStartTimeAsLocaltime() != null && request.getMedicationFrequency() == START_TIME_DURATION_FREQUENCY) {
            return SlotTimeCreationResult.withoutCrossingTags(getSlotsStartTimeWithStartTimeDurationFrequency(request, order));
        } else if ((!CollectionUtils.isEmpty(request.getFirstDaySlotsStartTimeAsLocalTime()) ||
                !CollectionUtils.isEmpty(request.getCrossingSlotsStartTimeAsLocalTime(null, null)) ||
                !CollectionUtils.isEmpty(request.getDayWiseSlotsStartTimeAsLocalTime()) ||
                !CollectionUtils.isEmpty(request.getRemainingDaySlotsStartTimeAsLocalTime()))
                        && request.getMedicationFrequency() == FIXED_SCHEDULE_FREQUENCY) {
            return getSlotsStartTimeWithFixedScheduleFrequency(request, order);
        }

        return SlotTimeCreationResult.withoutCrossingTags(Collections.emptyList());
    }

    /**
     * Core slot creation logic for FIXED_SCHEDULE medications.
     *
     * Builds complete medication schedule across prescription duration by:
     * 1. Adding first-day slots (partial day with custom times)
     * 2. Adding first-day crossing slots (doses that cross midnight)
     * 3. Adding remaining-day slots (final partial day if needed)
     * 4. Expanding day-wise pattern across available days (24-hour cycling)
     * 5. Tagging crossing slots with metadata (origin bucket, recurrence flag)
     *
     * Day-wise expansion: If prescription is 3 days, day-wise slots repeat on day 2 (and cross to day 3).
     * Each repeat is shifted by 24 hours until all slots are scheduled.
     *
     * @param request Contains first day, day-wise, remaining day, and crossing slot times
     * @param order The drug order with duration information
     * @return SlotTimeCreationResult with all slots and crossing slot metadata mapping
     */
    private SlotTimeCreationResult getSlotsStartTimeWithFixedScheduleFrequency(ScheduleMedicationRequest request, DrugOrder order) {
        int numberOfSlotsStartTimeToBeCreated = resolveSlotCountForFixedSchedule(request, order);
        if (numberOfSlotsStartTimeToBeCreated <= 0) {
            return SlotTimeCreationResult.withoutCrossingTags(Collections.emptyList());
        }

        LinkedHashSet<LocalDateTime> slotsStartTime = new LinkedHashSet<>();
        Map<LocalDateTime, SlotCrossingMetadata> crossingTagsByStartTime = new HashMap<>();

        numberOfSlotsStartTimeToBeCreated -= addDistinctSlots(
                slotsStartTime,
                request.getFirstDaySlotsStartTimeAsLocalTime(),
                numberOfSlotsStartTimeToBeCreated,
                null,
                crossingTagsByStartTime);

        // Crossings are always persisted from the explicit payload list.
        List<LocalDateTime> nonRecurringFirstDayCrossings =
                request.getCrossingSlotsStartTimeAsLocalTime(false, Slot.SourceBucket.FIRST_DAY);
        numberOfSlotsStartTimeToBeCreated -= addDistinctSlots(
                slotsStartTime,
                nonRecurringFirstDayCrossings,
                numberOfSlotsStartTimeToBeCreated,
                new SlotCrossingMetadata(Slot.SourceBucket.FIRST_DAY, false),
                crossingTagsByStartTime);

        List<LocalDateTime> recurringFirstDayCrossings =
                request.getCrossingSlotsStartTimeAsLocalTime(true, Slot.SourceBucket.FIRST_DAY);
        numberOfSlotsStartTimeToBeCreated -= addDistinctSlots(
                slotsStartTime,
                recurringFirstDayCrossings,
                numberOfSlotsStartTimeToBeCreated,
                new SlotCrossingMetadata(Slot.SourceBucket.FIRST_DAY, true),
                crossingTagsByStartTime);

        List<LocalDateTime> recurringDayWiseCrossings =
                request.getCrossingSlotsStartTimeAsLocalTime(true, Slot.SourceBucket.DAY_WISE);
        numberOfSlotsStartTimeToBeCreated -= addDistinctSlots(
                slotsStartTime,
                recurringDayWiseCrossings,
                numberOfSlotsStartTimeToBeCreated,
                new SlotCrossingMetadata(Slot.SourceBucket.DAY_WISE, true),
                crossingTagsByStartTime);

        numberOfSlotsStartTimeToBeCreated -= addDistinctSlots(
                slotsStartTime,
                request.getRemainingDaySlotsStartTimeAsLocalTime(),
                numberOfSlotsStartTimeToBeCreated,
                null,
                crossingTagsByStartTime);

        // Day-wise expansion should only use regular day-wise slots.
        List<LocalDateTime> dayWiseSlotsStartTimeFromRequest =
                request.getDayWiseSlotsStartTimeAsLocalTime() == null
                        ? Collections.emptyList()
                        : request.getDayWiseSlotsStartTimeAsLocalTime();

        List<LocalDateTime> cycleSlots = new ArrayList<>(dayWiseSlotsStartTimeFromRequest);
        while (numberOfSlotsStartTimeToBeCreated > 0 && !cycleSlots.isEmpty()) {
            int added = addDistinctSlots(
                    slotsStartTime,
                    cycleSlots,
                    numberOfSlotsStartTimeToBeCreated,
                    null,
                    crossingTagsByStartTime);

            if (added == 0) {
                cycleSlots = cycleSlots.stream()
                        .map(slotStartTime -> slotStartTime.plusHours(24))
                        .collect(Collectors.toList());
                continue;
            }

            numberOfSlotsStartTimeToBeCreated -= added;
            if (numberOfSlotsStartTimeToBeCreated > 0) {
                cycleSlots = cycleSlots.stream()
                        .map(slotStartTime -> slotStartTime.plusHours(24))
                        .collect(Collectors.toList());
            }
        }

        return new SlotTimeCreationResult(new ArrayList<>(slotsStartTime), crossingTagsByStartTime);
    }

    /**
     * Adds slots to accumulator, deduplicating and tagging crossing slots.
     *
     * Uses Set.add() to automatically skip duplicates. If a slot is successfully added AND
     * it has crossing metadata, stores the metadata for later persistence to database.
     *
     * @param accumulator Set of all slots (automatically deduplicates)
     * @param candidates Slot times to add (may include duplicates)
     * @param remainingCount How many more slots we need (stops early if reached)
     * @param crossingMetadata Metadata if these are crossing slots (null for regular slots)
     * @param crossingTagsByStartTime Map to store metadata for crossing slots
     * @return Number of new slots actually added (duplicates don't count)
     */
    private int addDistinctSlots(Set<LocalDateTime> accumulator,
                                 List<LocalDateTime> candidates,
                                 int remainingCount,
                                 SlotCrossingMetadata crossingMetadata,
                                 Map<LocalDateTime, SlotCrossingMetadata> crossingTagsByStartTime) {
        if (remainingCount <= 0 || CollectionUtils.isEmpty(candidates)) {
            return 0;
        }

        int added = 0;
        for (LocalDateTime candidate : candidates) {
            if (added >= remainingCount) {
                break;
            }
            if (candidate == null) {
                continue;
            }
            if (accumulator.add(candidate)) {
                added++;
            }
            if (crossingMetadata != null) {
                crossingTagsByStartTime.put(candidate, crossingMetadata);
            }
        }
        return added;
    }

    private int inferRequestedSlotsCount(ScheduleMedicationRequest request) {
        int firstDayCount = CollectionUtils.isEmpty(request.getFirstDaySlotsStartTimeAsLocalTime())
                ? 0
                : request.getFirstDaySlotsStartTimeAsLocalTime().size();
        int dayWiseCount = CollectionUtils.isEmpty(request.getDayWiseSlotsStartTimeAsLocalTime())
                ? 0
                : request.getDayWiseSlotsStartTimeAsLocalTime().size();
        int remainingCount = CollectionUtils.isEmpty(request.getRemainingDaySlotsStartTimeAsLocalTime())
                ? 0
                : request.getRemainingDaySlotsStartTimeAsLocalTime().size();
        int nonRecurringFirstDayCrossingCount = request
                .getCrossingSlotsStartTimeAsLocalTime(false, Slot.SourceBucket.FIRST_DAY)
                .size();
        int recurringFirstDayCrossingCount = request
                .getCrossingSlotsStartTimeAsLocalTime(true, Slot.SourceBucket.FIRST_DAY)
                .size();
        int recurringDayWiseCrossingCount = request
                .getCrossingSlotsStartTimeAsLocalTime(true, Slot.SourceBucket.DAY_WISE)
                .size();

        return firstDayCount
                + dayWiseCount
                + remainingCount
                + nonRecurringFirstDayCrossingCount
                + recurringFirstDayCrossingCount
                + recurringDayWiseCrossingCount;
    }

    private int resolveSlotCountForFixedSchedule(ScheduleMedicationRequest request, DrugOrder order) {
        Integer intradaySlotCount = resolveIntradaySlotCount(order);
        if (intradaySlotCount != null && intradaySlotCount > 0) {
            return intradaySlotCount;
        }

        if (order.getQuantity() == null || order.getDose() == null) {
            return inferRequestedSlotsCount(request);
        }
        return (int) Math.ceil(order.getQuantity() / order.getDose());
    }

    private Integer resolveIntradaySlotCount(DrugOrder order) {
        if (order == null || order.getDuration() == null || order.getDuration() <= 0) {
            return null;
        }

        if (!hasIntradayDoseFields(order.getDosingInstructions())) {
            return null;
        }

        return getIntradayFrequencyPerDay(order) * order.getDuration();
    }

    private boolean hasIntradayDoseFields(String dosingInstructions) {
        if (dosingInstructions == null || dosingInstructions.trim().isEmpty()) {
            return false;
        }

        try {
            JsonNode dosing = objectMapper.readTree(dosingInstructions);
            return dosing.isObject() && INTRADAY_DOSE_FIELDS.stream().anyMatch(dosing::has);
        } catch (Exception e) {
            log.warn("Failed to parse intraday dosingInstructions. Falling back to non-intraday flow.", e);
            return false;
        }
    }

    private int getIntradayFrequencyPerDay(DrugOrder order) {
        try {
            String dosingInstructions = order.getDosingInstructions();
            if (dosingInstructions == null || dosingInstructions.trim().isEmpty()) {
                log.warn("Intraday order " + order.getUuid() + " has empty dosingInstructions; falling back to 1 slot/day");
                return 1;
            }

            JsonNode dosing = objectMapper.readTree(dosingInstructions);
            if (!dosing.isObject()) {
                log.warn("Intraday order " + order.getUuid() + " has non-object dosingInstructions; falling back to 1 slot/day");
                return 1;
            }

            int count = (int) INTRADAY_DOSE_FIELDS.stream()
                    .filter(field -> dosing.path(field).asDouble(0) != 0)
                    .count();
            return count > 0 ? count : 1;
        } catch (Exception e) {
            log.warn("Failed to derive intraday frequency per day for order " + order.getUuid()
                    + " with dosingInstructions [" + order.getDosingInstructions() + "]", e);
            return 1;
        }
    }

    private List<LocalDateTime> getSlotsStartTimeWithStartTimeDurationFrequency(ScheduleMedicationRequest request, DrugOrder order) {
        int numberOfSlotsStartTimeToBeCreated = resolveSlotCountForStartTimeDuration(order, request.getVariableDosageSequence());
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

    private int resolveSlotCountForStartTimeDuration(DrugOrder order, Integer variableDosageSequence) {
        Integer intradaySlotCount = resolveIntradaySlotCount(order);
        if (intradaySlotCount != null && intradaySlotCount > 0) {
            return intradaySlotCount;
        }

        if (order.getFrequency() == null && variableDosageSequence != null && order.getDosingInstructions() != null) {
            return computeVdpNumberOfSlots(order, variableDosageSequence);
        }

        if (order.getQuantity() == 0.0 || order.getFrequency() == null || order.getDuration() == null || order.getDose() == null) {
            return 1;
        }
        return (int) Math.ceil(order.getQuantity() / order.getDose());
    }

    public HashMap<String , DrugOrderSchedule> getDrugOrderScheduledTime(Map<DrugOrder,List<Slot>> slotsByOrder){
        HashMap<String, DrugOrderSchedule> drugOrderScheduleHash= new HashMap<>();
        for (DrugOrder drugOrder : slotsByOrder.keySet()) {
            DrugOrderSchedule drugOrderSchedule = new DrugOrderSchedule();
            List<Slot> orderSlots = slotsByOrder.get(drugOrder);
            if (CollectionUtils.isEmpty(orderSlots)) {
                continue;
            }

            boolean hasSingleSlot = orderSlots.size() == 1;
            if (drugOrder.getAsNeeded() || hasSingleSlot || drugOrder.getQuantity() == 0.0) {
                drugOrderSchedule.setSlotStartTime(DateTimeUtil.convertLocalDateTimeToUTCEpoc(orderSlots.get(0).getStartDateTime()));
            }
            else {
                String frequency = drugOrder.getFrequency() != null ? drugOrder.getFrequency().getName() : null;
                List<CrossingSlotDTO> crossingSlots = extractCrossingSlots(orderSlots);

                // Crossing slots are now tagged at save time via tagCrossingSlots().
                // Pre-existing orders without tags will have empty crossingSlots until re-saved.

                if (frequency != null && START_TIME_FREQUENCIES.contains(frequency)) {
                    List<Slot> sortedSlots = orderSlots.stream()
                            .filter(slot -> slot.getStartDateTime() != null)
                            .sorted(Comparator.comparing(Slot::getStartDateTime))
                            .collect(Collectors.toList());
                    if (!CollectionUtils.isEmpty(sortedSlots)) {
                        drugOrderSchedule.setSlotStartTime(DateTimeUtil.convertLocalDateTimeToUTCEpoc(sortedSlots.get(0).getStartDateTime()));
                    }
                } else {
                    applyBucketedSchedule(orderSlots, drugOrderSchedule);
                }

                drugOrderSchedule.setCrossingSlots(crossingSlots);

            }

            if (!CollectionUtils.isEmpty(drugOrderSchedule.getFirstDaySlotsStartTime())) {
                drugOrderSchedule.setFirstDaySlotsStartTime(deduplicateEpochs(drugOrderSchedule.getFirstDaySlotsStartTime()));
            }
            if (!CollectionUtils.isEmpty(drugOrderSchedule.getDayWiseSlotsStartTime())) {
                drugOrderSchedule.setDayWiseSlotsStartTime(deduplicateEpochs(drugOrderSchedule.getDayWiseSlotsStartTime()));
            }
            if (!CollectionUtils.isEmpty(drugOrderSchedule.getRemainingDaySlotsStartTime())) {
                drugOrderSchedule.setRemainingDaySlotsStartTime(deduplicateEpochs(drugOrderSchedule.getRemainingDaySlotsStartTime()));
            }

            List<StageScheduleStatus> stageSchedules = buildStageSchedules(orderSlots);
            if (!CollectionUtils.isEmpty(stageSchedules)) {
                drugOrderSchedule.setStageSchedules(stageSchedules);
            }

            drugOrderSchedule.setSlots(slotsByOrder.get(drugOrder));
            drugOrderScheduleHash.put(drugOrder.getUuid(),drugOrderSchedule);
        }
        return drugOrderScheduleHash;
    }

    private int computeVdpNumberOfSlots(DrugOrder order, Integer sequence) {
        try {
            JsonNode dosages = objectMapper.readTree(order.getDosingInstructions());
            for (JsonNode dosage : dosages) {
                if (dosage.path("sequence").asInt() != sequence) continue;
                if (dosage.path("timing").path("repeat").path("count").asInt(0) == 1) {
                    return 1;
                }
                double duration = dosage.path("timing").path("repeat").path("duration").asDouble(0);
                String durationUnit = dosage.path("timing").path("repeat").path("durationUnit").asText("d");
                double durationDays = normalizeFhirDurationToDays(duration, durationUnit);
                String frequencyName = dosage.path("timing").path("code").path("text").asText(null);
                double frequencyPerDay = getFrequencyPerDayByName(frequencyName);
                return (int) Math.ceil(durationDays * frequencyPerDay);
            }
        } catch (Exception e) {
            log.warn("Failed to compute VDP numberOfSlots from FHIR for order " + order.getUuid() + " sequence " + sequence, e);
        }
        return 1;
    }

    private double getFrequencyPerDayFromFhir(DrugOrder order, Integer sequence) {
        try {
            JsonNode dosages = objectMapper.readTree(order.getDosingInstructions());
            for (JsonNode dosage : dosages) {
                if (dosage.path("sequence").asInt() != sequence) continue;
                String frequencyName = dosage.path("timing").path("code").path("text").asText(null);
                return getFrequencyPerDayByName(frequencyName);
            }
        } catch (Exception e) {
            log.warn("Failed to get frequencyPerDay from FHIR for order " + order.getUuid() + " sequence " + sequence, e);
        }
        return 1.0;
    }

    private double getFrequencyPerDayByName(String frequencyName) {
        if (frequencyName == null) return 1.0;
        java.util.List<org.openmrs.OrderFrequency> frequencies = org.openmrs.api.context.Context.getOrderService().getOrderFrequencies(false);
        if (frequencies == null) return 1.0;
        return frequencies.stream()
            .filter(f -> frequencyName.equals(f.getConcept().getName().getName()))
            .findFirst()
            .map(f -> f.getFrequencyPerDay())
            .orElse(1.0);
    }

    private double normalizeFhirDurationToDays(double duration, String durationUnit) {
        switch (durationUnit != null ? durationUnit : "d") {
            case "wk": return duration * 7;
            case "mo": return duration * 30;
            default:
                log.warn("Unknown FHIR duration unit: " + durationUnit + "; treating as days");
                return duration;
        }
    }

    private List<Long> deduplicateEpochs(List<Long> epochs) {
        return new ArrayList<>(new LinkedHashSet<>(epochs));
    }


    public List<StageScheduleStatus> buildStageSchedules(List<Slot> orderSlots) {
        Map<Integer, List<Slot>> slotsBySequence = orderSlots.stream()
                .filter(slot -> slot.getVariableDosageSequence() != null)
                .collect(Collectors.groupingBy(Slot::getVariableDosageSequence, TreeMap::new, Collectors.toList()));

        if (slotsBySequence.isEmpty()) {
            return Collections.emptyList();
        }

        List<StageScheduleStatus> stageSchedules = new ArrayList<>();
        for (Map.Entry<Integer, List<Slot>> entry : slotsBySequence.entrySet()) {
            Integer sequence = entry.getKey();
            List<Slot> stageSlots = entry.getValue().stream()
                    .sorted(Comparator.comparing(Slot::getStartDateTime))
                    .collect(Collectors.toList());

            List<CrossingSlotDTO> stageCrossingSlots = extractCrossingSlots(stageSlots);
            StageScheduleStatus.StageScheduleStatusBuilder builder = StageScheduleStatus.builder()
                    .variableDosageSequence(sequence)
                    .isScheduled(!stageSlots.isEmpty())
                    .administrationStarted(stageSlots.stream().anyMatch(s -> s.getMedicationAdministration() != null))
                    .allAttended(stageSlots.stream().noneMatch(s -> s.getStatus().equals(Slot.SlotStatus.SCHEDULED)))
                    .pendingSlotsAvailable(stageSlots.stream().anyMatch(s ->
                            s.getStartDateTime() != null &&
                                    LocalDateTime.now().isBefore(s.getStartDateTime()) &&
                                    s.getStatus().equals(Slot.SlotStatus.SCHEDULED)))
                    .notes(stageSlots.get(0).getNotes())
                    .crossingSlots(stageCrossingSlots);

            if (isStartTimeFrequencyForStage(stageSlots.get(0), sequence)) {
                stageSlots.stream()
                        .filter(slot -> slot.getStartDateTime() != null)
                        .map(Slot::getStartDateTime)
                        .min(Comparator.naturalOrder())
                        .map(DateTimeUtil::convertLocalDateTimeToUTCEpoc)
                        .ifPresent(builder::slotStartTime);
            } else {
                BucketedSlots bucketedSlots = bucketSlots(stageSlots);
                if (bucketedSlots.slotStartTime != null) {
                    builder.slotStartTime(DateTimeUtil.convertLocalDateTimeToUTCEpoc(bucketedSlots.slotStartTime));
                }
                if (!CollectionUtils.isEmpty(bucketedSlots.firstDaySlotsStartTime)) {
                    builder.firstDaySlotsStartTime(bucketedSlots.firstDaySlotsStartTime.stream()
                            .map(DateTimeUtil::convertLocalDateTimeToUTCEpoc)
                            .collect(Collectors.toList()));
                }
                if (!CollectionUtils.isEmpty(bucketedSlots.dayWiseSlotsStartTime)) {
                    builder.dayWiseSlotsStartTime(bucketedSlots.dayWiseSlotsStartTime.stream()
                            .map(DateTimeUtil::convertLocalDateTimeToUTCEpoc)
                            .collect(Collectors.toList()));
                }
                if (!CollectionUtils.isEmpty(bucketedSlots.remainingDaySlotsStartTime)) {
                    builder.remainingDaySlotsStartTime(bucketedSlots.remainingDaySlotsStartTime.stream()
                            .map(DateTimeUtil::convertLocalDateTimeToUTCEpoc)
                            .collect(Collectors.toList()));
                }
            }

            StageScheduleStatus scheduleStatus = builder.build();
            stageSchedules.add(StageScheduleStatus.builder()
                    .variableDosageSequence(scheduleStatus.getVariableDosageSequence())
                    .isScheduled(scheduleStatus.getIsScheduled())
                    .administrationStarted(scheduleStatus.getAdministrationStarted())
                    .allAttended(scheduleStatus.getAllAttended())
                    .pendingSlotsAvailable(scheduleStatus.getPendingSlotsAvailable())
                    .notes(scheduleStatus.getNotes())
                    .slotStartTime(scheduleStatus.getSlotStartTime())
                    .firstDaySlotsStartTime(deduplicateEpochsNullable(scheduleStatus.getFirstDaySlotsStartTime()))
                    .dayWiseSlotsStartTime(deduplicateEpochsNullable(scheduleStatus.getDayWiseSlotsStartTime()))
                    .remainingDaySlotsStartTime(deduplicateEpochsNullable(scheduleStatus.getRemainingDaySlotsStartTime()))
                    .crossingSlots(scheduleStatus.getCrossingSlots())
                    .build());
        }
        return stageSchedules;
    }

    private List<Long> deduplicateEpochsNullable(List<Long> epochs) {
        if (CollectionUtils.isEmpty(epochs)) {
            return epochs;
        }
        return deduplicateEpochs(epochs);
    }

    private boolean isStartTimeFrequencyForStage(Slot slot, Integer sequence) {
        try {
            DrugOrder drugOrder = (DrugOrder) slot.getOrder();
            if (drugOrder == null || drugOrder.getDosingInstructions() == null) {
                return true;
            }

            JsonNode dosages = objectMapper.readTree(drugOrder.getDosingInstructions());
            if (!dosages.isArray()) {
                return true;
            }

            for (JsonNode dosage : dosages) {
                if (dosage.path("sequence").asInt() != sequence) {
                    continue;
                }
                if (dosage.path("timing").path("repeat").path("count").asInt(0) == 1) {
                    return true;
                }

                String frequencyName = dosage.path("timing").path("code").path("text").asText(null);
                return START_TIME_FREQUENCIES.contains(frequencyName);
            }
        } catch (Exception e) {
            log.warn("Failed to parse dosingInstructions for frequency determination. Defaulting to START_TIME_FREQUENCY. Order: " +
                    (slot.getOrder() != null ? slot.getOrder().getUuid() : "unknown"), e);
        }
        return true;
    }

    private void applyBucketedSchedule(List<Slot> slots, DrugOrderSchedule schedule) {
        BucketedSlots bucketedSlots = bucketSlots(slots);

        if (bucketedSlots.slotStartTime != null) {
            schedule.setSlotStartTime(DateTimeUtil.convertLocalDateTimeToUTCEpoc(bucketedSlots.slotStartTime));
        }
        if (!CollectionUtils.isEmpty(bucketedSlots.firstDaySlotsStartTime)) {
            schedule.setFirstDaySlotsStartTime(bucketedSlots.firstDaySlotsStartTime.stream()
                    .map(DateTimeUtil::convertLocalDateTimeToUTCEpoc)
                    .collect(Collectors.toList()));
        }
        if (!CollectionUtils.isEmpty(bucketedSlots.dayWiseSlotsStartTime)) {
            schedule.setDayWiseSlotsStartTime(bucketedSlots.dayWiseSlotsStartTime.stream()
                    .map(DateTimeUtil::convertLocalDateTimeToUTCEpoc)
                    .collect(Collectors.toList()));
        }
        if (!CollectionUtils.isEmpty(bucketedSlots.remainingDaySlotsStartTime)) {
            schedule.setRemainingDaySlotsStartTime(bucketedSlots.remainingDaySlotsStartTime.stream()
                    .map(DateTimeUtil::convertLocalDateTimeToUTCEpoc)
                    .collect(Collectors.toList()));
        }
    }

    /**
     * Separates persisted slots into dose-schedule buckets: first day, day-wise, remaining day.
     *
     * Slots are categorized by date position and origin bucket:
     * - First day: Slots on the prescription's first date
     * - Day-wise: Slots on middle dates (days that repeat the pattern)
     * - Remaining: Slots on the last date (partial day at end)
     * - Crossings: Slots with originDoseBucket set (midnight crossers) - not bucketed here
     *
     * Special handling: Terminal day-wise crossing (last midnight crossing) is added to
     * remaining slots so it's visible in the response.
     *
     * @param slots Persisted Slot records from database
     * @return BucketedSlots with slots organized by date position
     */
    private BucketedSlots bucketSlots(List<Slot> slots) {
        List<Slot> sortedSlots = slots.stream()
                .filter(slot -> slot.getStartDateTime() != null)
                .sorted(Comparator.comparing(Slot::getStartDateTime))
                .collect(Collectors.toList());

        if (CollectionUtils.isEmpty(sortedSlots)) {
            return BucketedSlots.empty();
        }

        Map<LocalDate, List<LocalDateTime>> nonCrossingByDate = sortedSlots.stream()
                .filter(slot -> slot.getOriginDoseBucket() == null)
                .collect(Collectors.groupingBy(
                        slot -> slot.getStartDateTime().toLocalDate(),
                        TreeMap::new,
                        Collectors.mapping(Slot::getStartDateTime, Collectors.toList())
                ));

        List<LocalDateTime> firstDaySlots = new ArrayList<>();
        List<LocalDateTime> dayWiseSlots = new ArrayList<>();
        List<LocalDateTime> remainingDaySlots = new ArrayList<>();

        List<LocalDateTime> firstDayCrossings = sortedSlots.stream()
                .filter(slot -> Slot.SourceBucket.FIRST_DAY.equals(slot.getOriginDoseBucket()))
                .map(Slot::getStartDateTime)
                .sorted()
                .collect(Collectors.toList());

        List<LocalDateTime> dayWiseCrossings = sortedSlots.stream()
                .filter(slot -> Slot.SourceBucket.DAY_WISE.equals(slot.getOriginDoseBucket()))
                .map(Slot::getStartDateTime)
                .sorted()
                .collect(Collectors.toList());

        if (!nonCrossingByDate.isEmpty()) {
            List<LocalDate> orderedDates = new ArrayList<>(nonCrossingByDate.keySet());
            if (orderedDates.isEmpty()) {
                return BucketedSlots.empty();
            }
            LocalDate firstDate = orderedDates.get(0);
            LocalDate lastDate = orderedDates.get(orderedDates.size() - 1);

            firstDaySlots.addAll(nonCrossingByDate.getOrDefault(firstDate, Collections.emptyList()));

            if (orderedDates.size() == 1) {
                dayWiseSlots.addAll(nonCrossingByDate.getOrDefault(firstDate, Collections.emptyList()));
                firstDaySlots.clear();
            } else {
                for (int index = 1; index < orderedDates.size() - 1; index++) {
                    dayWiseSlots.addAll(nonCrossingByDate.getOrDefault(orderedDates.get(index), Collections.emptyList()));
                }
                remainingDaySlots.addAll(nonCrossingByDate.getOrDefault(lastDate, Collections.emptyList()));
            }
        }

        Collections.sort(firstDaySlots);
        Collections.sort(dayWiseSlots);
        Collections.sort(remainingDaySlots);

        // Keep only the terminal day-wise crossing in remainder so the last-day
        // midnight spillover is visible in remainder slots without polluting
        // non-terminal remainder days.
        if (!CollectionUtils.isEmpty(dayWiseCrossings)) {
            LocalDateTime terminalDayWiseCrossing = dayWiseCrossings.get(dayWiseCrossings.size() - 1);
            if (!remainingDaySlots.contains(terminalDayWiseCrossing)) {
                remainingDaySlots.add(terminalDayWiseCrossing);
                Collections.sort(remainingDaySlots);
            }
        }

        return new BucketedSlots(
                sortedSlots.get(0).getStartDateTime(),
                firstDaySlots,
                dayWiseSlots,
                remainingDaySlots
        );
    }

    /**
     * Converts persisted Slot records to CrossingSlotDTO for API response.
     *
     * Only extracts slots with originDoseBucket set (i.e., crossing slots).
     * Deduplicates by (epoch + originDoseBucket + isRecurringAcrossDays) composite key,
     * keeping first occurrence. Maintains insertion order for consistent response.
     *
     * @param slots Persisted Slot records (may include both regular and crossing slots)
     * @return List of CrossingSlotDTO with metadata for API response
     */
    private List<CrossingSlotDTO> extractCrossingSlots(List<Slot> slots) {
        return slots.stream()
                .filter(slot -> slot.getOriginDoseBucket() != null)
                .map(slot -> CrossingSlotDTO.builder()
                        .epoch(DateTimeUtil.convertLocalDateTimeToUTCEpoc(slot.getStartDateTime()))
                        .isRecurringAcrossDays(slot.getIsRecurringAcrossDays())
                        .originDoseBucket(slot.getOriginDoseBucket())
                        .build())
                .collect(Collectors.collectingAndThen(
                        Collectors.toMap(
                                slot -> slot.getEpoch() + "|" + slot.getOriginDoseBucket() + "|" + slot.getIsRecurringAcrossDays(),
                                slot -> slot,
                                (first, ignored) -> first,
                                LinkedHashMap::new
                        ),
                        map -> new ArrayList<>(map.values())
                ));
    }

    private static class BucketedSlots {
        private final LocalDateTime slotStartTime;
        private final List<LocalDateTime> firstDaySlotsStartTime;
        private final List<LocalDateTime> dayWiseSlotsStartTime;
        private final List<LocalDateTime> remainingDaySlotsStartTime;

        private BucketedSlots(LocalDateTime slotStartTime,
                              List<LocalDateTime> firstDaySlotsStartTime,
                              List<LocalDateTime> dayWiseSlotsStartTime,
                              List<LocalDateTime> remainingDaySlotsStartTime) {
            this.slotStartTime = slotStartTime;
            this.firstDaySlotsStartTime = firstDaySlotsStartTime;
            this.dayWiseSlotsStartTime = dayWiseSlotsStartTime;
            this.remainingDaySlotsStartTime = remainingDaySlotsStartTime;
        }

        private static BucketedSlots empty() {
            return new BucketedSlots(null, Collections.emptyList(), Collections.emptyList(), Collections.emptyList());
        }
    }
}
