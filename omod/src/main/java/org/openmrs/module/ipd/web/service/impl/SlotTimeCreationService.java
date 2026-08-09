package org.openmrs.module.ipd.web.service.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.openmrs.DrugOrder;
import org.openmrs.api.impl.BaseOpenmrsService;
import org.openmrs.module.ipd.api.util.DateTimeUtil;
import org.openmrs.module.ipd.web.model.CrossingSlotTag;
import org.openmrs.module.ipd.web.model.DrugOrderSchedule;
import org.openmrs.module.ipd.web.model.SlotTimeCreationResult;
import org.openmrs.module.ipd.api.model.Slot;
import org.openmrs.module.ipd.web.contract.CrossingSlotContract;
import org.openmrs.module.ipd.web.contract.ScheduleMedicationRequest;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

import static org.openmrs.module.ipd.web.contract.ScheduleMedicationRequest.MedicationFrequency.FIXED_SCHEDULE_FREQUENCY;
import static org.openmrs.module.ipd.web.contract.ScheduleMedicationRequest.MedicationFrequency.START_TIME_DURATION_FREQUENCY;

@Service
@Component
public class SlotTimeCreationService extends BaseOpenmrsService {

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

        // Recurring day-wise midnight-crossing slot: shifted forward to align with the final day bucket.
        List<LocalDateTime> recurringDayWiseCrossings =
                request.getCrossingSlotsStartTimeAsLocalTime(true, Slot.SourceBucket.DAY_WISE);
        List<LocalDateTime> shiftedDayWiseCrossings = Collections.emptyList();
        if (!CollectionUtils.isEmpty(recurringDayWiseCrossings)) {
            long offsetDays = 1;
            if (!CollectionUtils.isEmpty(dayWiseSlotsStartTimeFromRequest) && !CollectionUtils.isEmpty(remainingDaySlotsStartTime)) {
                offsetDays = ChronoUnit.DAYS.between(
                        dayWiseSlotsStartTimeFromRequest.get(0).toLocalDate(),
                        remainingDaySlotsStartTime.get(0).toLocalDate());
            }
            final long finalOffsetDays = offsetDays;
            shiftedDayWiseCrossings = recurringDayWiseCrossings.stream()
                    .map(t -> t.plusDays(finalOffsetDays))
                    .collect(Collectors.toList());
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
                List<CrossingSlotContract> crossingSlots = slotsByOrder.get(drugOrder).stream()
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

                if (CollectionUtils.isEmpty(crossingSlots)) {
                    crossingSlots = readPersistedCrossingSlots(slotsByOrder.get(drugOrder), drugOrder.getUuid());
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
}
