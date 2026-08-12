package org.openmrs.module.ipd.web.service.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.openmrs.DrugOrder;
import org.openmrs.api.impl.BaseOpenmrsService;
import org.openmrs.module.ipd.api.util.DateTimeUtil;
import org.openmrs.module.ipd.web.model.CrossingSlotTag;
import org.openmrs.module.ipd.web.model.DrugOrderSchedule;
import org.openmrs.module.ipd.web.model.StageScheduleStatus;
import org.openmrs.module.ipd.web.model.SlotTimeCreationResult;
import org.openmrs.module.ipd.api.model.Slot;
import org.openmrs.module.ipd.web.contract.CrossingSlotContract;
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
    private static final String CROSSING_SLOTS_BY_ORDER_KEY = "crossingSlotsByOrder";
    private final ObjectMapper objectMapper = new ObjectMapper();

    public static final List<String> START_TIME_FREQUENCIES= Arrays.asList(new String[]{"Every Hour", "Every 2 hours", "Every 3 hours", "Every 4 hours", "Every 6 hours", "Every 8 hours", "Every 12 hours", "Once a day", "Nocte (At Night)", "Every 30 minutes", "STAT (Immediately)", "In Afternoon", "In Morning", "Once a week", "Twice a week", "Three times a week", "Four days a week", "Five days a week", "Six days a week", "On alternate days", "Monthly", "Once a month", "Every 2 weeks", "Every 3 weeks"});

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

    private SlotTimeCreationResult getSlotsStartTimeWithFixedScheduleFrequency(ScheduleMedicationRequest request, DrugOrder order) {
        int numberOfSlotsStartTimeToBeCreated;
        if (order.getQuantity() == null || order.getDose() == null) {
            numberOfSlotsStartTimeToBeCreated = inferRequestedSlotsCount(request);
            if (numberOfSlotsStartTimeToBeCreated <= 0) {
                return SlotTimeCreationResult.withoutCrossingTags(Collections.emptyList());
            }
        } else {
            numberOfSlotsStartTimeToBeCreated = (int) (Math.ceil(order.getQuantity() / order.getDose()));
        }

        List<LocalDateTime> slotsStartTime = new ArrayList<>();
        Map<LocalDateTime, CrossingSlotTag> crossingTagsByStartTime = new HashMap<>();

        if (!CollectionUtils.isEmpty(request.getFirstDaySlotsStartTimeAsLocalTime())) {
            List<LocalDateTime> slotsToBeAddedForFirstDay = numberOfSlotsStartTimeToBeCreated < request.getFirstDaySlotsStartTimeAsLocalTime().size()
                ? request.getFirstDaySlotsStartTimeAsLocalTime().subList(0, numberOfSlotsStartTimeToBeCreated)
                : request.getFirstDaySlotsStartTimeAsLocalTime();

            slotsStartTime.addAll(slotsToBeAddedForFirstDay);
            numberOfSlotsStartTimeToBeCreated -= slotsToBeAddedForFirstDay.size();
        }

        // Non-recurring first-day midnight-crossing slot: added once, never replicated across days.
        List<LocalDateTime> nonRecurringFirstDayCrossings =
                request.getCrossingSlotsStartTimeAsLocalTime(false, Slot.SourceBucket.FIRST_DAY);
        if (!CollectionUtils.isEmpty(nonRecurringFirstDayCrossings) && numberOfSlotsStartTimeToBeCreated > 0) {
            List<LocalDateTime> toAdd = numberOfSlotsStartTimeToBeCreated < nonRecurringFirstDayCrossings.size()
                    ? nonRecurringFirstDayCrossings.subList(0, numberOfSlotsStartTimeToBeCreated)
                    : nonRecurringFirstDayCrossings;
            slotsStartTime.addAll(toAdd);
            toAdd.forEach(t -> crossingTagsByStartTime.put(t, new CrossingSlotTag(Slot.SourceBucket.FIRST_DAY, false)));
            numberOfSlotsStartTimeToBeCreated -= toAdd.size();
        }

        // Recurring first-day midnight-crossing slot: joins the day-wise recurring pattern.
        List<LocalDateTime> dayWiseSlotsStartTimeFromRequest = request.getDayWiseSlotsStartTimeAsLocalTime();
        List<LocalDateTime> nextSlotsStartTime = new ArrayList<>(
                dayWiseSlotsStartTimeFromRequest != null ? dayWiseSlotsStartTimeFromRequest : Collections.emptyList()
        );
        List<LocalDateTime> recurringFirstDayCrossings =
                request.getCrossingSlotsStartTimeAsLocalTime(true, Slot.SourceBucket.FIRST_DAY);
        nextSlotsStartTime.addAll(recurringFirstDayCrossings);
        Set<LocalDateTime> recurringFirstDayCrossingsSet = new HashSet<>(recurringFirstDayCrossings);

        List<LocalDateTime> remainingDaySlotsStartTime = new ArrayList<>(
                request.getRemainingDaySlotsStartTimeAsLocalTime() != null ? request.getRemainingDaySlotsStartTimeAsLocalTime() : Collections.emptyList()
        );

        // Recurring day-wise midnight-crossing slot: persist in the exact epoch supplied by UI.
        List<LocalDateTime> recurringDayWiseCrossings =
                request.getCrossingSlotsStartTimeAsLocalTime(true, Slot.SourceBucket.DAY_WISE);
        List<LocalDateTime> shiftedDayWiseCrossings = Collections.emptyList();
        if (!CollectionUtils.isEmpty(recurringDayWiseCrossings)) {
            shiftedDayWiseCrossings = recurringDayWiseCrossings;
            remainingDaySlotsStartTime.addAll(shiftedDayWiseCrossings);
        }
        Set<LocalDateTime> shiftedDayWiseCrossingsSet = new HashSet<>(shiftedDayWiseCrossings);

        if (!CollectionUtils.isEmpty(remainingDaySlotsStartTime) && numberOfSlotsStartTimeToBeCreated > 0) {
            List<LocalDateTime> slotsToBeAddedForRemainingDay = numberOfSlotsStartTimeToBeCreated < remainingDaySlotsStartTime.size()
                    ? remainingDaySlotsStartTime.subList(0, numberOfSlotsStartTimeToBeCreated)
                    : remainingDaySlotsStartTime;
            numberOfSlotsStartTimeToBeCreated -= slotsToBeAddedForRemainingDay.size();
            slotsStartTime.addAll(slotsToBeAddedForRemainingDay);
            slotsToBeAddedForRemainingDay.stream()
                    .filter(shiftedDayWiseCrossingsSet::contains)
                    .forEach(t -> crossingTagsByStartTime.put(t, new CrossingSlotTag(Slot.SourceBucket.DAY_WISE, true)));
        }

        if (!CollectionUtils.isEmpty(nextSlotsStartTime) && numberOfSlotsStartTimeToBeCreated > 0) {
            List<LocalDateTime> initialSlotsToBeAddedForSecondDay = numberOfSlotsStartTimeToBeCreated < nextSlotsStartTime.size()
                    ? nextSlotsStartTime.subList(0, numberOfSlotsStartTimeToBeCreated)
                    : nextSlotsStartTime;
            slotsStartTime.addAll(initialSlotsToBeAddedForSecondDay);
            // Only tag the first occurrence of a recurring first-day crossing slot; later
            // replicated days don't need their own tag since the response only needs one
            // representative epoch per crossing item.
            initialSlotsToBeAddedForSecondDay.stream()
                    .filter(recurringFirstDayCrossingsSet::contains)
                    .forEach(t -> crossingTagsByStartTime.put(t, new CrossingSlotTag(Slot.SourceBucket.FIRST_DAY, true)));
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

        return new SlotTimeCreationResult(slotsStartTime, crossingTagsByStartTime);
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

    private List<LocalDateTime> getSlotsStartTimeWithStartTimeDurationFrequency(ScheduleMedicationRequest request, DrugOrder order) {
        int numberOfSlotsStartTimeToBeCreated = (order.getQuantity() == 0.0 || order.getFrequency() == null || order.getDuration() == null) ? 1 : (int) (Math.ceil(order.getQuantity() / order.getDose()));
        List<LocalDateTime> slotsStartTime = new ArrayList<>();
        Double slotDurationInHours =  order.getFrequency() != null ? 24 / order.getFrequency().getFrequencyPerDay() : 0;
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
                List<CrossingSlotContract> crossingSlots = extractCrossingSlots(orderSlots);

                if (CollectionUtils.isEmpty(crossingSlots)) {
                    crossingSlots = readPersistedCrossingSlots(orderSlots, drugOrder.getUuid());
                }

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

    private List<Long> deduplicateEpochs(List<Long> epochs) {
        return new ArrayList<>(new LinkedHashSet<>(epochs));
    }

    private List<CrossingSlotContract> readPersistedCrossingSlots(List<Slot> orderSlots, String orderUuid) {
        if (CollectionUtils.isEmpty(orderSlots) || orderSlots.get(0).getSchedule() == null) {
            return Collections.emptyList();
        }

        String comments = orderSlots.get(0).getSchedule().getComments();
        if (comments == null || comments.trim().isEmpty()) {
            return Collections.emptyList();
        }

        try {
            Map<String, Object> root = objectMapper.readValue(comments, new TypeReference<Map<String, Object>>() {});
            Object byOrderObj = root.get(CROSSING_SLOTS_BY_ORDER_KEY);
            if (byOrderObj == null) {
                return Collections.emptyList();
            }
            Map<String, List<CrossingSlotContract>> byOrder = objectMapper.convertValue(
                    byOrderObj,
                    new TypeReference<Map<String, List<CrossingSlotContract>>>() {}
            );
            return byOrder.getOrDefault(orderUuid, Collections.emptyList());
        } catch (Exception ignored) {
            return Collections.emptyList();
        }
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

            StageScheduleStatus.StageScheduleStatusBuilder builder = StageScheduleStatus.builder()
                    .variableDosageSequence(sequence)
                    .isScheduled(!stageSlots.isEmpty())
                    .administrationStarted(stageSlots.stream().anyMatch(s -> s.getMedicationAdministration() != null))
                    .allAttended(stageSlots.stream().noneMatch(s -> s.getStatus().equals(Slot.SlotStatus.SCHEDULED)))
                    .pendingSlotsAvailable(stageSlots.stream().anyMatch(s ->
                            s.getStartDateTime() != null &&
                                    LocalDateTime.now().isBefore(s.getStartDateTime()) &&
                                    s.getStatus().equals(Slot.SlotStatus.SCHEDULED)))
                    .notes(stageSlots.get(0).getNotes());

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
            List<CrossingSlotContract> stageCrossingSlots = extractCrossingSlots(stageSlots);

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
                    .crossingSlots(stageCrossingSlots)
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

    private BucketedSlots bucketSlots(List<Slot> slots) {
        List<Slot> sortedSlots = slots.stream()
                .filter(slot -> slot.getStartDateTime() != null)
                .sorted(Comparator.comparing(Slot::getStartDateTime))
                .collect(Collectors.toList());

        if (CollectionUtils.isEmpty(sortedSlots)) {
            return BucketedSlots.empty();
        }

        Map<LocalDate, List<LocalDateTime>> nonCrossingByDate = sortedSlots.stream()
                .filter(slot -> slot.getSourceBucket() == null)
                .collect(Collectors.groupingBy(
                        slot -> slot.getStartDateTime().toLocalDate(),
                        TreeMap::new,
                        Collectors.mapping(Slot::getStartDateTime, Collectors.toList())
                ));

        List<LocalDateTime> firstDaySlots = new ArrayList<>();
        List<LocalDateTime> dayWiseSlots = new ArrayList<>();
        List<LocalDateTime> remainingDaySlots = new ArrayList<>();

        List<LocalDateTime> firstDayCrossings = sortedSlots.stream()
                .filter(slot -> Slot.SourceBucket.FIRST_DAY.equals(slot.getSourceBucket()))
                .map(Slot::getStartDateTime)
                .sorted()
                .collect(Collectors.toList());

        List<LocalDateTime> dayWiseCrossings = sortedSlots.stream()
                .filter(slot -> Slot.SourceBucket.DAY_WISE.equals(slot.getSourceBucket()))
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

        dayWiseSlots.addAll(firstDayCrossings);
        remainingDaySlots.addAll(dayWiseCrossings);

        Collections.sort(firstDaySlots);
        Collections.sort(dayWiseSlots);
        Collections.sort(remainingDaySlots);

        return new BucketedSlots(
                sortedSlots.get(0).getStartDateTime(),
                firstDaySlots,
                dayWiseSlots,
                remainingDaySlots
        );
    }

    private List<CrossingSlotContract> extractCrossingSlots(List<Slot> slots) {
        return slots.stream()
                .filter(slot -> slot.getSourceBucket() != null)
                .map(slot -> CrossingSlotContract.builder()
                        .epoch(DateTimeUtil.convertLocalDateTimeToUTCEpoc(slot.getStartDateTime()))
                        .recurring(slot.getRecurringCrossing())
                        .sourceBucket(slot.getSourceBucket())
                        .build())
                .collect(Collectors.collectingAndThen(
                        Collectors.toMap(
                                slot -> slot.getEpoch() + "|" + slot.getSourceBucket() + "|" + slot.getRecurring(),
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
