package com.platform.solver;

import ai.timefold.solver.core.api.domain.entity.PlanningEntity;
import ai.timefold.solver.core.api.domain.lookup.PlanningId;
import ai.timefold.solver.core.api.domain.solution.PlanningEntityCollectionProperty;
import ai.timefold.solver.core.api.domain.solution.PlanningScore;
import ai.timefold.solver.core.api.domain.solution.PlanningSolution;
import ai.timefold.solver.core.api.domain.solution.ProblemFactCollectionProperty;
import ai.timefold.solver.core.api.domain.valuerange.ValueRangeProvider;
import ai.timefold.solver.core.api.domain.variable.PlanningVariable;
import ai.timefold.solver.core.api.score.buildin.hardsoft.HardSoftScore;
import ai.timefold.solver.core.api.score.stream.Constraint;
import ai.timefold.solver.core.api.score.stream.ConstraintFactory;
import ai.timefold.solver.core.api.score.stream.ConstraintProvider;
import ai.timefold.solver.core.api.solver.Solver;
import ai.timefold.solver.core.api.solver.SolverFactory;
import ai.timefold.solver.core.config.solver.SolverConfig;
import ai.timefold.solver.core.config.solver.termination.TerminationConfig;
import com.platform.entity.Appointment;
import com.platform.entity.ProviderProfile;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
public class TimefoldBookingService {

    private static final List<LocalTime> DEFAULT_DAILY_SLOTS = List.of(
            LocalTime.of(9, 0),
            LocalTime.of(10, 0),
            LocalTime.of(11, 0),
            LocalTime.of(13, 0),
            LocalTime.of(14, 0),
            LocalTime.of(15, 0)
    );

    private static final Pattern HOURS_RANGE_PATTERN = Pattern.compile("(\\d{2}:\\d{2})\\s*-\\s*(\\d{2}:\\d{2})");

    public static class CandidateSlot {
        @PlanningId
        private String id;
        private LocalDateTime startTime;
        private LocalDateTime endTime;
        private boolean withinWorkingHours;
        private boolean inPast;

        public CandidateSlot() {
        }

        public CandidateSlot(
                String id,
                LocalDateTime startTime,
                LocalDateTime endTime,
                boolean withinWorkingHours,
                boolean inPast
        ) {
            this.id = id;
            this.startTime = startTime;
            this.endTime = endTime;
            this.withinWorkingHours = withinWorkingHours;
            this.inPast = inPast;
        }

        public String getId() {
            return id;
        }

        public LocalDateTime getStartTime() {
            return startTime;
        }

        public LocalDateTime getEndTime() {
            return endTime;
        }

        public boolean isWithinWorkingHours() {
            return withinWorkingHours;
        }

        public boolean isInPast() {
            return inPast;
        }
    }

    public static class ExistingBookingFact {
        @PlanningId
        private UUID id;
        private UUID therapistUuid;
        private LocalDateTime startTime;
        private LocalDateTime endTime;

        public ExistingBookingFact() {
        }

        public ExistingBookingFact(UUID id, UUID therapistUuid, LocalDateTime startTime, LocalDateTime endTime) {
            this.id = id != null ? id : UUID.randomUUID();
            this.therapistUuid = therapistUuid;
            this.startTime = startTime;
            this.endTime = endTime;
        }

        public UUID getId() {
            return id;
        }

        public UUID getTherapistUuid() {
            return therapistUuid;
        }

        public LocalDateTime getStartTime() {
            return startTime;
        }

        public LocalDateTime getEndTime() {
            return endTime;
        }

        public boolean overlaps(LocalDateTime otherStart, LocalDateTime otherEnd) {
            return otherStart.isBefore(this.endTime) && otherEnd.isAfter(this.startTime);
        }
    }

    @PlanningEntity
    public static class BookingAssignment {
        @PlanningId
        private UUID id;
        private UUID therapistUuid;
        private int durationMinutes;
        private LocalDateTime preferredStartTime;

        @PlanningVariable(valueRangeProviderRefs = "candidateSlotRange")
        private CandidateSlot assignedSlot;

        public BookingAssignment() {
        }

        public BookingAssignment(
                UUID id,
                UUID therapistUuid,
                int durationMinutes,
                LocalDateTime preferredStartTime,
                CandidateSlot assignedSlot
        ) {
            this.id = id;
            this.therapistUuid = therapistUuid;
            this.durationMinutes = durationMinutes;
            this.preferredStartTime = preferredStartTime;
            this.assignedSlot = assignedSlot;
        }

        public UUID getId() {
            return id;
        }

        public UUID getTherapistUuid() {
            return therapistUuid;
        }

        public int getDurationMinutes() {
            return durationMinutes;
        }

        public LocalDateTime getPreferredStartTime() {
            return preferredStartTime;
        }

        public CandidateSlot getAssignedSlot() {
            return assignedSlot;
        }

        public void setAssignedSlot(CandidateSlot assignedSlot) {
            this.assignedSlot = assignedSlot;
        }
    }

    @PlanningSolution
    public static class TherapistScheduleSolution {
        @ValueRangeProvider(id = "candidateSlotRange")
        @ProblemFactCollectionProperty
        private List<CandidateSlot> candidateSlots;

        @ProblemFactCollectionProperty
        private List<ExistingBookingFact> existingBookings;

        @PlanningEntityCollectionProperty
        private List<BookingAssignment> assignments;

        @PlanningScore
        private HardSoftScore score;

        public TherapistScheduleSolution() {
        }

        public TherapistScheduleSolution(
                List<CandidateSlot> candidateSlots,
                List<ExistingBookingFact> existingBookings,
                List<BookingAssignment> assignments
        ) {
            this.candidateSlots = candidateSlots;
            this.existingBookings = existingBookings;
            this.assignments = assignments;
        }

        public List<CandidateSlot> getCandidateSlots() {
            return candidateSlots;
        }

        public List<ExistingBookingFact> getExistingBookings() {
            return existingBookings;
        }

        public List<BookingAssignment> getAssignments() {
            return assignments;
        }

        public HardSoftScore getScore() {
            return score;
        }

        public void setScore(HardSoftScore score) {
            this.score = score;
        }
    }

    public static class BookingConstraintProvider implements ConstraintProvider {
        @Override
        public Constraint[] defineConstraints(ConstraintFactory factory) {
            return new Constraint[]{
                    noOverlapWithExistingBookings(factory),
                    noPastBookingConstraint(factory),
                    withinWorkingHoursConstraint(factory),
                    preferRequestedStartTime(factory),
                    preferCompactScheduleAdjacentToExisting(factory)
            };
        }

        private Constraint noOverlapWithExistingBookings(ConstraintFactory factory) {
            return factory.forEach(BookingAssignment.class)
                    .join(ExistingBookingFact.class)
                    .filter((assignment, existing) ->
                            assignment.getAssignedSlot() != null
                                    && Objects.equals(assignment.getTherapistUuid(), existing.getTherapistUuid())
                                    && existing.overlaps(
                                            assignment.getAssignedSlot().getStartTime(),
                                            assignment.getAssignedSlot().getEndTime()
                                    ))
                    .penalize(HardSoftScore.ofHard(100))
                    .asConstraint("No overlap with existing therapist bookings");
        }

        private Constraint noPastBookingConstraint(ConstraintFactory factory) {
            return factory.forEach(BookingAssignment.class)
                    .filter(a -> a.getAssignedSlot() != null && a.getAssignedSlot().isInPast())
                    .penalize(HardSoftScore.ofHard(50))
                    .asConstraint("Cannot schedule appointment in the past");
        }

        private Constraint withinWorkingHoursConstraint(ConstraintFactory factory) {
            return factory.forEach(BookingAssignment.class)
                    .filter(a -> a.getAssignedSlot() != null && !a.getAssignedSlot().isWithinWorkingHours())
                    .penalize(HardSoftScore.ofHard(10))
                    .asConstraint("Must be within therapist working hours");
        }

        private Constraint preferRequestedStartTime(ConstraintFactory factory) {
            return factory.forEach(BookingAssignment.class)
                    .filter(a -> a.getAssignedSlot() != null && a.getPreferredStartTime() != null)
                    .penalize(HardSoftScore.ONE_SOFT, a -> {
                        long minutesDiff = Math.abs(Duration.between(
                                a.getPreferredStartTime(),
                                a.getAssignedSlot().getStartTime()
                        ).toMinutes());
                        return (int) Math.min(1000, minutesDiff / 15);
                    })
                    .asConstraint("Minimize distance from preferred time");
        }

        private Constraint preferCompactScheduleAdjacentToExisting(ConstraintFactory factory) {
            return factory.forEach(BookingAssignment.class)
                    .join(ExistingBookingFact.class)
                    .filter((assignment, existing) -> {
                        if (assignment.getAssignedSlot() == null) return false;
                        LocalDateTime slotStart = assignment.getAssignedSlot().getStartTime();
                        LocalDateTime slotEnd = assignment.getAssignedSlot().getEndTime();
                        if (!slotStart.toLocalDate().equals(existing.getStartTime().toLocalDate())) {
                            return false;
                        }
                        return slotStart.equals(existing.getEndTime()) || slotEnd.equals(existing.getStartTime());
                    })
                    .reward(HardSoftScore.ofSoft(12))
                    .asConstraint("Reward back-to-back compact therapist schedule");
        }
    }

    private final SolverFactory<TherapistScheduleSolution> solverFactory;

    public TimefoldBookingService() {
        SolverConfig solverConfig = new SolverConfig()
                .withSolutionClass(TherapistScheduleSolution.class)
                .withEntityClasses(BookingAssignment.class)
                .withConstraintProviderClass(BookingConstraintProvider.class)
                .withTerminationConfig(new TerminationConfig()
                        .withMillisecondsSpentLimit(120L)
                        .withUnimprovedMillisecondsSpentLimit(40L));
        this.solverFactory = SolverFactory.create(solverConfig);
    }

    public Map<String, Object> suggestOptimalSlots(
            UUID therapistUuid,
            LocalDate date,
            int durationMinutes,
            LocalTime preferredTime,
            List<Appointment> existingAppointments,
            ProviderProfile providerProfile,
            UUID excludeAppointmentId
    ) {
        int effectiveDuration = durationMinutes > 0 ? durationMinutes : 60;
        LocalTime targetPreferred = preferredTime != null ? preferredTime : LocalTime.of(10, 0);
        LocalDateTime preferredDateTime = LocalDateTime.of(date, targetPreferred);
        LocalDateTime now = LocalDateTime.now();

        LocalTime[] workingWindow = parseWorkingWindow(providerProfile != null ? providerProfile.getWorkingHoursJson() : null);
        LocalTime workStart = workingWindow[0];
        LocalTime workEnd = workingWindow[1];

        List<ExistingBookingFact> existingFacts = (existingAppointments == null ? List.<Appointment>of() : existingAppointments)
                .stream()
                .filter(a -> a != null
                        && !"CANCELLED".equalsIgnoreCase(a.getStatus())
                        && a.getStartTime() != null
                        && a.getEndTime() != null
                        && (excludeAppointmentId == null || !excludeAppointmentId.equals(a.getId())))
                .map(a -> new ExistingBookingFact(a.getId(), therapistUuid, a.getStartTime(), a.getEndTime()))
                .collect(Collectors.toList());

        List<CandidateSlot> candidateSlots = new ArrayList<>();
        for (LocalTime slotTime : DEFAULT_DAILY_SLOTS) {
            LocalDateTime start = LocalDateTime.of(date, slotTime);
            LocalDateTime end = start.plusMinutes(effectiveDuration);
            boolean withinHours = !slotTime.isBefore(workStart) && !end.toLocalTime().isAfter(workEnd);
            boolean inPast = start.isBefore(now);
            candidateSlots.add(new CandidateSlot(slotTime.toString(), start, end, withinHours, inPast));
        }

        BookingAssignment assignment = new BookingAssignment(
                UUID.randomUUID(),
                therapistUuid,
                effectiveDuration,
                preferredDateTime,
                candidateSlots.get(0)
        );

        TherapistScheduleSolution problem = new TherapistScheduleSolution(
                candidateSlots,
                existingFacts,
                List.of(assignment)
        );

        Solver<TherapistScheduleSolution> solver = solverFactory.buildSolver();
        TherapistScheduleSolution solved = solver.solve(problem);

        CandidateSlot bestSlot = solved.getAssignments().get(0).getAssignedSlot();
        HardSoftScore bestScore = solved.getScore();

        List<Map<String, Object>> evaluatedSlots = new ArrayList<>();
        for (CandidateSlot candidate : candidateSlots) {
            boolean overlaps = existingFacts.stream()
                    .anyMatch(f -> f.overlaps(candidate.getStartTime(), candidate.getEndTime()));
            boolean feasible = !overlaps && !candidate.isInPast() && candidate.isWithinWorkingHours();

            int hardPenalty = (overlaps ? -100 : 0)
                    + (candidate.isInPast() ? -50 : 0)
                    + (!candidate.isWithinWorkingHours() ? -10 : 0);

            long minutesFromPreferred = Math.abs(Duration.between(preferredDateTime, candidate.getStartTime()).toMinutes());
            int softScore = -(int) Math.min(1000, minutesFromPreferred / 15);
            boolean adjacentBonus = existingFacts.stream().anyMatch(f ->
                    f.getStartTime().toLocalDate().equals(date)
                            && (candidate.getStartTime().equals(f.getEndTime())
                                    || candidate.getEndTime().equals(f.getStartTime()))
            );
            if (adjacentBonus) {
                softScore += 12;
            }

            String reason;
            if (overlaps) {
                reason = "CONFLICT_OVERLAP";
            } else if (candidate.isInPast()) {
                reason = "IN_PAST";
            } else if (!candidate.isWithinWorkingHours()) {
                reason = "OUTSIDE_WORKING_HOURS";
            } else if (adjacentBonus) {
                reason = "OPTIMAL_ADJACENT_SLOT";
            } else {
                reason = "AVAILABLE";
            }

            Map<String, Object> slotInfo = new LinkedHashMap<>();
            slotInfo.put("slot", candidate.getId());
            slotInfo.put("startTime", candidate.getStartTime().toString());
            slotInfo.put("endTime", candidate.getEndTime().toString());
            slotInfo.put("available", feasible);
            slotInfo.put("overlaps", overlaps);
            slotInfo.put("inPast", candidate.isInPast());
            slotInfo.put("withinWorkingHours", candidate.isWithinWorkingHours());
            slotInfo.put("hardScore", hardPenalty);
            slotInfo.put("softScore", softScore);
            slotInfo.put("score", hardPenalty + "hard/" + softScore + "soft");
            slotInfo.put("reason", reason);
            slotInfo.put("recommended", feasible && bestSlot != null && candidate.getId().equals(bestSlot.getId()));
            evaluatedSlots.add(slotInfo);
        }

        List<Map<String, Object>> rankedAvailable = evaluatedSlots.stream()
                .filter(s -> Boolean.TRUE.equals(s.get("available")))
                .sorted(Comparator.comparingInt((Map<String, Object> s) -> (Integer) s.get("softScore")).reversed())
                .collect(Collectors.toList());

        String recommendedSlotStr = !rankedAvailable.isEmpty()
                ? String.valueOf(rankedAvailable.get(0).get("slot"))
                : (bestSlot != null && bestScore != null && bestScore.isFeasible() ? bestSlot.getId() : null);

        for (Map<String, Object> slotInfo : evaluatedSlots) {
            slotInfo.put("recommended", recommendedSlotStr != null && recommendedSlotStr.equals(slotInfo.get("slot")));
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("solver", "Timefold Solver 1.16.0");
        result.put("therapistUuid", therapistUuid);
        result.put("date", date.toString());
        result.put("durationMinutes", effectiveDuration);
        result.put("workingHoursWindow", workStart + "-" + workEnd);
        result.put("recommendedSlot", recommendedSlotStr);
        result.put("solverScore", bestScore != null ? bestScore.toString() : "0hard/0soft");
        result.put("feasible", recommendedSlotStr != null);
        result.put("slots", evaluatedSlots);
        return result;
    }

    public LocalTime[] parseWorkingWindow(String workingHoursJson) {
        if (workingHoursJson != null && !workingHoursJson.isBlank()) {
            Matcher matcher = HOURS_RANGE_PATTERN.matcher(workingHoursJson);
            if (matcher.find()) {
                try {
                    LocalTime start = LocalTime.parse(matcher.group(1));
                    LocalTime end = LocalTime.parse(matcher.group(2));
                    if (end.isAfter(start)) {
                        return new LocalTime[]{start, end};
                    }
                } catch (Exception ignored) {
                }
            }
        }
        return new LocalTime[]{LocalTime.of(8, 0), LocalTime.of(17, 0)};
    }
}
