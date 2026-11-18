package com.platform.features.appointment;

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
import ai.timefold.solver.core.api.score.stream.Joiners;
import ai.timefold.solver.core.api.solver.SolverFactory;
import ai.timefold.solver.core.config.solver.SolverConfig;
import ai.timefold.solver.core.config.solver.termination.TerminationConfig;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.platform.config.PlatformCacheService;
import com.platform.config.PlatformEventService;
import com.platform.core.BaseDomainCore.BaseRepository;
import com.platform.core.BaseDomainCore.BaseService;
import com.platform.core.BaseDomainCore.ErrorCode;
import com.platform.core.BaseDomainCore.HasId;
import com.platform.core.BaseDomainCore.Identifiable;
import com.platform.core.BaseWebCore.BaseController;
import com.platform.features.clinic.ClinicPlatformFeature.ProviderProfile;
import com.platform.features.clinic.ClinicPlatformFeature.ProviderProfilePort;
import com.platform.features.clinic.ClinicPlatformFeature.ServiceLookupPort;
import com.platform.features.journal.JournalFeature;
import com.platform.features.journal.JournalFeature.JournalGitService;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Repository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * ============================================================================
 * ARKITEKTONISK VISITKORT: AppointmentFeature (Vertical Feature Slice)
 * ============================================================================
 * @Spejler: frontend/src/features/appointment/AppointmentFeature.tsx
 *           frontend/src/features/booking/BookingFeature.tsx
 *
 * FORMÅL (Til Censor, Vejleder & Nykommende Udviklere):
 * Samler hele bookingdomænet, kalenderstyring og AI-baseret tidsoptimering
 * (Timefold Solver) i én selvbærende vertikal slice efter 5-trins strukturen:
 *
 * ARKITEKTONISK OVERBLIK (5 TRIN):
 *   1. [Record/DTO: AppointmentDto]
 *      Uforanderlig Java 17 `record` der implementerer [Interface: HasId].
 *   2. [Entity: Appointment]
 *      JPA-entitet i tabellen `appointments` (implementerer [Interface: Identifiable]).
 *   3. [Interface: AppointmentPort] & [Repository: AppointmentRepository]
 *      Offentligt port-interface til tværgående moduler, mens selve JPA-repositoriet
 *      holdes package-private (Information Hiding).
 *   4. [Service: TimefoldBookingService] & [Service: AppointmentService]
 *      - [Service: TimefoldBookingService]: Evaluerer Hard Constraints (ingen overlap,
 *        ikke i fortiden, inden for arbejdstid) og Soft Constraints (tættest på ønsket
 *        tidspunkt samt sammenhængende back-to-back kalender) via `HardSoftScore`.
 *      - [Service: AppointmentService]: Arver fra [AbstractClass: BaseService<Appointment, AppointmentDto>],
 *        håndhæver dobbeltbooking-guards (#isTimeSlotOverlapping) og skriver et
 *        kryptografisk JGit-commit med `Refs-Appointment: #<uuid>` ved hver ændring.
 *   5. [Controller: AppointmentController]
 *      Arver standard CRUD fra [AbstractClass: BaseController<Appointment, AppointmentDto>]
 *      og tilføjer `/timefold-slots` og `/{id}/git-history`.
 * ============================================================================
 */
public final class AppointmentFeature {

    private AppointmentFeature() {}

    // =========================================================================
    // 1. DTO CONTRACTS (Public Java Records implementing HasId)
    // =========================================================================

    public record AppointmentDto(
            UUID id,
            UUID clientUuid,
            UUID therapistUuid,
            UUID serviceId,
            String clientName,
            String therapistName,
            String organisationName,
            String location,
            LocalDateTime startTime,
            LocalDateTime endTime,
            String title,
            String status,
            String gitCommitHash,
            UUID journalEntryId
    ) implements HasId {

        public AppointmentDto(
                UUID id,
                UUID clientUuid,
                UUID therapistUuid,
                UUID serviceId,
                String therapistName,
                String organisationName,
                String location,
                LocalDateTime startTime,
                LocalDateTime endTime,
                String title,
                String status,
                String gitCommitHash,
                UUID journalEntryId
        ) {
            this(id, clientUuid, therapistUuid, serviceId, null, therapistName, organisationName, location,
                    startTime, endTime, title, status, gitCommitHash, journalEntryId);
        }

        public AppointmentDto(UUID id, String clientName, LocalDateTime startTime) {
            this(id, null, null, null, clientName, null, null, null,
                    startTime, startTime != null ? startTime.plusHours(1) : null, clientName, "CONFIRMED", null, null);
        }

        public AppointmentDto(
                UUID clientUuid,
                UUID therapistUuid,
                LocalDateTime startTime,
                LocalDateTime endTime,
                String title,
                String status
        ) {
            this(null, clientUuid, therapistUuid, null, null, null, null, null,
                    startTime, endTime, title, status, null, null);
        }

        public UUID getId() { return id; }
        public UUID getClientUuid() { return clientUuid; }
        public UUID getTherapistUuid() { return therapistUuid; }
        public UUID getServiceId() { return serviceId; }
        public String getClientName() { return clientName; }
        public String getTherapistName() { return therapistName; }
        public String getOrganisationName() { return organisationName; }
        public String getLocation() { return location; }
        public LocalDateTime getStartTime() { return startTime; }
        public LocalDateTime getEndTime() { return endTime; }
        public String getTitle() { return title; }
        public String getStatus() { return status; }
        public String getGitCommitHash() { return gitCommitHash; }
        public UUID getJournalEntryId() { return journalEntryId; }
    }

    // =========================================================================
    // 2. JPA / MONGO ENTITIES (Implements Identifiable)
    // =========================================================================

    @Entity(name = "Appointment")
    @Table(name = "appointments")
    public static class Appointment implements Identifiable {

        @Id
        @GeneratedValue(strategy = GenerationType.UUID)
        private UUID id;

        @Column
        private UUID clientUuid;

        @Column
        private UUID therapistUuid;

        @Column(name = "service_id")
        private UUID serviceId;

        @Column(name = "client_name")
        private String clientName;

        @Column(name = "therapist_name")
        private String therapistName;

        @Column(name = "organisation_name")
        private String organisationName;

        @Column
        private String location;

        @Column(nullable = false)
        private LocalDateTime startTime;

        @Column(nullable = false)
        private LocalDateTime endTime;

        @Column
        private String title;

        @Column
        private String status;

        @Column(name = "git_commit_hash")
        private String gitCommitHash;

        @Column(name = "journal_entry_id")
        private UUID journalEntryId;

        public Appointment() {}

        public Appointment(UUID id, String clientName, LocalDateTime startTime) {
            this.id = id;
            this.clientName = clientName;
            this.title = clientName;
            this.startTime = startTime;
            this.endTime = startTime != null ? startTime.plusHours(1) : null;
            this.status = "CONFIRMED";
        }

        public Appointment(
                UUID clientUuid,
                UUID therapistUuid,
                LocalDateTime startTime,
                LocalDateTime endTime,
                String title,
                String status
        ) {
            this.clientUuid = clientUuid;
            this.therapistUuid = therapistUuid;
            this.startTime = startTime;
            this.endTime = endTime;
            this.title = title;
            this.status = status;
        }

        @Override public UUID getId() { return id; }
        @Override public void setId(UUID id) { this.id = id; }

        public UUID getClientUuid() { return clientUuid; }
        public void setClientUuid(UUID clientUuid) { this.clientUuid = clientUuid; }

        public UUID getTherapistUuid() { return therapistUuid; }
        public void setTherapistUuid(UUID therapistUuid) { this.therapistUuid = therapistUuid; }

        public UUID getServiceId() { return serviceId; }
        public void setServiceId(UUID serviceId) { this.serviceId = serviceId; }

        public String getClientName() { return clientName; }
        public void setClientName(String clientName) { this.clientName = clientName; }

        public String getTherapistName() { return therapistName; }
        public void setTherapistName(String therapistName) { this.therapistName = therapistName; }

        public String getOrganisationName() { return organisationName; }
        public void setOrganisationName(String organisationName) { this.organisationName = organisationName; }

        public String getLocation() { return location; }
        public void setLocation(String location) { this.location = location; }

        public LocalDateTime getStartTime() { return startTime; }
        public void setStartTime(LocalDateTime startTime) { this.startTime = startTime; }

        public LocalDateTime getEndTime() { return endTime; }
        public void setEndTime(LocalDateTime endTime) { this.endTime = endTime; }

        public String getTitle() { return title; }
        public void setTitle(String title) { this.title = title; }

        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }

        public String getGitCommitHash() { return gitCommitHash; }
        public void setGitCommitHash(String gitCommitHash) { this.gitCommitHash = gitCommitHash; }

        public UUID getJournalEntryId() { return journalEntryId; }
        public void setJournalEntryId(UUID journalEntryId) { this.journalEntryId = journalEntryId; }
    }

    // =========================================================================
    // 2. TIMEFOLD SOLVER (Public)
    // =========================================================================

    @Service
    public static class TimefoldBookingService {

        private static final int SLOT_STEP_MINUTES = 30;
        private static final int OUTSIDE_HOURS_START_MINUTE = 7 * 60;
        private static final int OUTSIDE_HOURS_END_MINUTE = 21 * 60;
        private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
        private static final Pattern HOURS_RANGE_PATTERN = Pattern.compile("(\\d{2}:\\d{2})\\s*-\\s*(\\d{2}:\\d{2})");

        public record TimeWindowFact(@PlanningId String id, int startMinute, int endMinute) {
            public boolean contains(LocalDateTime startTime, LocalDateTime endTime) {
                if (startTime == null || endTime == null) {
                    return false;
                }
                int slotStart = startTime.getHour() * 60 + startTime.getMinute();
                boolean endsAtMidnight = endTime.toLocalTime().equals(LocalTime.MIDNIGHT)
                        && endTime.toLocalDate().equals(startTime.toLocalDate().plusDays(1));
                int slotEnd = endsAtMidnight ? 1440 : (endTime.getHour() * 60 + endTime.getMinute());
                boolean sameDayOrMidnight = startTime.toLocalDate().equals(endTime.toLocalDate()) || endsAtMidnight;
                return sameDayOrMidnight && slotStart >= startMinute && slotEnd <= endMinute && slotEnd > slotStart;
            }

            public String formatWindow() {
                return formatMinute(startMinute) + "-" + formatMinute(endMinute);
            }

            private static String formatMinute(int minuteOfDay) {
                return String.format("%02d:%02d", (minuteOfDay / 60) % 24, minuteOfDay % 60);
            }
        }

        public static class CandidateSlot {
            @PlanningId
            private String id;
            private LocalDateTime startTime;
            private LocalDateTime endTime;
            private boolean withinWorkingHours;
            private boolean inPast;

            public CandidateSlot() {}

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

            public String getId() { return id; }
            public LocalDateTime getStartTime() { return startTime; }
            public LocalDateTime getEndTime() { return endTime; }
            public boolean isWithinWorkingHours() { return withinWorkingHours; }
            public boolean isInPast() { return inPast; }
        }

        public static class ExistingBookingFact {
            @PlanningId
            private UUID id;
            private UUID therapistUuid;
            private LocalDateTime startTime;
            private LocalDateTime endTime;

            public ExistingBookingFact() {}

            public ExistingBookingFact(UUID id, UUID therapistUuid, LocalDateTime startTime, LocalDateTime endTime) {
                this.id = id != null ? id : UUID.randomUUID();
                this.therapistUuid = therapistUuid;
                this.startTime = startTime;
                this.endTime = endTime;
            }

            public UUID getId() { return id; }
            public UUID getTherapistUuid() { return therapistUuid; }
            public LocalDateTime getStartTime() { return startTime; }
            public LocalDateTime getEndTime() { return endTime; }

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

            public BookingAssignment() {}

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

            public UUID getId() { return id; }
            public UUID getTherapistUuid() { return therapistUuid; }
            public int getDurationMinutes() { return durationMinutes; }
            public LocalDateTime getPreferredStartTime() { return preferredStartTime; }
            public CandidateSlot getAssignedSlot() { return assignedSlot; }
            public void setAssignedSlot(CandidateSlot assignedSlot) { this.assignedSlot = assignedSlot; }
        }

        @PlanningSolution
        public static class TherapistScheduleSolution {
            @ValueRangeProvider(id = "candidateSlotRange")
            @ProblemFactCollectionProperty
            private List<CandidateSlot> candidateSlots;

            @ProblemFactCollectionProperty
            private List<ExistingBookingFact> existingBookings;

            @ProblemFactCollectionProperty
            private List<TimeWindowFact> workingWindows = List.of();

            @PlanningEntityCollectionProperty
            private List<BookingAssignment> assignments;

            @PlanningScore
            private HardSoftScore score;

            public TherapistScheduleSolution() {}

            public TherapistScheduleSolution(
                    List<CandidateSlot> candidateSlots,
                    List<ExistingBookingFact> existingBookings,
                    List<BookingAssignment> assignments,
                    List<TimeWindowFact> workingWindows
            ) {
                this.candidateSlots = candidateSlots;
                this.existingBookings = existingBookings;
                this.assignments = assignments;
                this.workingWindows = workingWindows;
            }

            public List<CandidateSlot> getCandidateSlots() { return candidateSlots; }
            public List<ExistingBookingFact> getExistingBookings() { return existingBookings; }
            public List<TimeWindowFact> getWorkingWindows() { return workingWindows; }
            public List<BookingAssignment> getAssignments() { return assignments; }
            public HardSoftScore getScore() { return score; }
            public void setScore(HardSoftScore score) { this.score = score; }
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
                                Objects.equals(assignment.getTherapistUuid(), existing.getTherapistUuid())
                                        && existing.overlaps(
                                                assignment.getAssignedSlot().getStartTime(),
                                                assignment.getAssignedSlot().getEndTime()
                                        ))
                        .penalize(HardSoftScore.ofHard(100))
                        .asConstraint("No overlap with existing therapist bookings");
            }

            private Constraint noPastBookingConstraint(ConstraintFactory factory) {
                return factory.forEach(BookingAssignment.class)
                        .filter(a -> a.getAssignedSlot().isInPast())
                        .penalize(HardSoftScore.ofHard(50))
                        .asConstraint("Cannot schedule appointment in the past");
            }

            private Constraint withinWorkingHoursConstraint(ConstraintFactory factory) {
                return factory.forEach(BookingAssignment.class)
                        .ifNotExists(
                                TimeWindowFact.class,
                                Joiners.filtering((assignment, window) ->
                                        window.contains(
                                                assignment.getAssignedSlot().getStartTime(),
                                                assignment.getAssignedSlot().getEndTime()
                                        ))
                        )
                        .penalize(HardSoftScore.ofHard(10))
                        .asConstraint("Must be within therapist working hours");
            }

            private Constraint preferRequestedStartTime(ConstraintFactory factory) {
                return factory.forEach(BookingAssignment.class)
                        .filter(a -> a.getPreferredStartTime() != null)
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
            return suggestOptimalSlots(
                    therapistUuid,
                    date,
                    durationMinutes,
                    preferredTime,
                    existingAppointments,
                    providerProfile,
                    excludeAppointmentId,
                    false
            );
        }

        public Map<String, Object> suggestOptimalSlots(
                UUID therapistUuid,
                LocalDate date,
                int durationMinutes,
                LocalTime preferredTime,
                List<Appointment> existingAppointments,
                ProviderProfile providerProfile,
                UUID excludeAppointmentId,
                boolean includeOutsideHours
        ) {
            int effectiveDuration = durationMinutes > 0 ? durationMinutes : 60;
            LocalTime targetPreferred = preferredTime != null ? preferredTime : LocalTime.of(10, 0);
            LocalDateTime preferredDateTime = LocalDateTime.of(date, targetPreferred);
            LocalDateTime now = LocalDateTime.now();

            List<TimeWindowFact> normalWindows = resolveWorkingWindowsForDate(providerProfile, date);
            List<TimeWindowFact> solverWindows = includeOutsideHours
                    ? List.of(new TimeWindowFact("extended-0", OUTSIDE_HOURS_START_MINUTE, OUTSIDE_HOURS_END_MINUTE))
                    : normalWindows;
            String workingHoursSummary = normalWindows.isEmpty()
                    ? "Lukket"
                    : normalWindows.stream().map(TimeWindowFact::formatWindow).collect(Collectors.joining(", "));

            List<ExistingBookingFact> existingFacts = toExistingFacts(therapistUuid, existingAppointments, excludeAppointmentId);

            List<CandidateSlot> candidateSlots = buildCandidateSlots(date, effectiveDuration, now, normalWindows, solverWindows);
            if (candidateSlots.isEmpty()) {
                Map<String, Object> closedResult = new LinkedHashMap<>();
                closedResult.put("solver", "Timefold Solver 1.16.0");
                closedResult.put("therapistUuid", therapistUuid);
                closedResult.put("date", date.toString());
                closedResult.put("durationMinutes", effectiveDuration);
                closedResult.put("workingHoursWindow", workingHoursSummary);
                closedResult.put("closed", normalWindows.isEmpty());
                closedResult.put("recommendedSlot", null);
                closedResult.put("solverScore", "0hard/0soft");
                closedResult.put("feasible", false);
                closedResult.put("slots", List.of());
                return closedResult;
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
                    List.of(assignment),
                    solverWindows
            );

            TherapistScheduleSolution solved = solverFactory.buildSolver().solve(problem);
            HardSoftScore bestScore = solved.getScore();

            List<Map<String, Object>> evaluatedSlots = new ArrayList<>();
            for (CandidateSlot candidate : candidateSlots) {
                evaluatedSlots.add(evaluateCandidateSlot(
                        candidate, date, preferredDateTime, existingFacts, includeOutsideHours
                ));
            }

            List<Map<String, Object>> rankedAvailable = evaluatedSlots.stream()
                    .filter(s -> Boolean.TRUE.equals(s.get("available")))
                    .sorted(Comparator.comparingInt((Map<String, Object> s) -> (Integer) s.get("softScore")).reversed())
                    .collect(Collectors.toList());

            String recommendedSlotStr = !rankedAvailable.isEmpty()
                    ? String.valueOf(rankedAvailable.get(0).get("slot"))
                    : null;

            for (Map<String, Object> slotInfo : evaluatedSlots) {
                slotInfo.put("recommended", recommendedSlotStr != null && recommendedSlotStr.equals(slotInfo.get("slot")));
            }

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("solver", "Timefold Solver 1.16.0");
            result.put("therapistUuid", therapistUuid);
            result.put("date", date.toString());
            result.put("durationMinutes", effectiveDuration);
            result.put("workingHoursWindow", workingHoursSummary);
            result.put("closed", false);
            result.put("recommendedSlot", recommendedSlotStr);
            result.put("solverScore", String.valueOf(bestScore));
            result.put("feasible", recommendedSlotStr != null);
            result.put("slots", evaluatedSlots);
            return result;
        }

        public void assertBookingAllowed(
                UUID therapistUuid,
                LocalDateTime startTime,
                LocalDateTime endTime,
                List<Appointment> existingAppointments,
                ProviderProfile providerProfile,
                UUID excludeAppointmentId,
                boolean enforceWorkingHours
        ) {
            if (therapistUuid == null || startTime == null || endTime == null) {
                return;
            }
            List<ExistingBookingFact> existingFacts = toExistingFacts(therapistUuid, existingAppointments, excludeAppointmentId);
            boolean hasConfiguredSchedule = providerProfile != null
                    && providerProfile.getWorkingHoursJson() != null
                    && !providerProfile.getWorkingHoursJson().isBlank();
            List<TimeWindowFact> constraintWindows = (enforceWorkingHours && hasConfiguredSchedule)
                    ? resolveWorkingWindowsForDate(providerProfile, startTime.toLocalDate())
                    : List.of(new TimeWindowFact("allow-all", 0, 1440));

            CandidateSlot requestedSlot = new CandidateSlot(
                    "requested",
                    startTime,
                    endTime,
                    fitsAnyWindow(constraintWindows, startTime, endTime),
                    false
            );
            int durationMinutes = (int) Math.max(1L, Duration.between(startTime, endTime).toMinutes());
            BookingAssignment assignment = new BookingAssignment(
                    UUID.randomUUID(), therapistUuid, durationMinutes, startTime, requestedSlot
            );
            TherapistScheduleSolution problem = new TherapistScheduleSolution(
                    List.of(requestedSlot), existingFacts, List.of(assignment), constraintWindows
            );

            TherapistScheduleSolution solved = solverFactory.buildSolver().solve(problem);
            HardSoftScore score = solved.getScore();
            if (score.hardScore() < 0) {
                boolean overlaps = existingFacts.stream().anyMatch(f -> f.overlaps(startTime, endTime));
                ErrorCode.CONFLICT.require(
                        !overlaps,
                        "Timefold AI rejected booking: Therapist is already booked during the requested slot"
                );
                throw ErrorCode.CONFLICT.toException(
                        "Timefold AI rejected booking: Requested time is outside the therapist's available working hours"
                );
            }
        }

        private List<ExistingBookingFact> toExistingFacts(
                UUID therapistUuid,
                List<Appointment> existingAppointments,
                UUID excludeAppointmentId
        ) {
            return (existingAppointments == null ? List.<Appointment>of() : existingAppointments)
                    .stream()
                    .filter(a -> a != null
                            && !"CANCELLED".equalsIgnoreCase(a.getStatus())
                            && a.getStartTime() != null
                            && a.getEndTime() != null
                            && (excludeAppointmentId == null || !excludeAppointmentId.equals(a.getId())))
                    .map(a -> new ExistingBookingFact(a.getId(), therapistUuid, a.getStartTime(), a.getEndTime()))
                    .collect(Collectors.toList());
        }

        private List<CandidateSlot> buildCandidateSlots(
                LocalDate date,
                int effectiveDuration,
                LocalDateTime now,
                List<TimeWindowFact> normalWindows,
                List<TimeWindowFact> generationWindows
        ) {
            List<CandidateSlot> candidateSlots = new ArrayList<>();
            Set<Integer> seenStarts = new LinkedHashSet<>();
            for (TimeWindowFact win : generationWindows) {
                for (int m = win.startMinute(); m + effectiveDuration <= win.endMinute(); m += SLOT_STEP_MINUTES) {
                    if (!seenStarts.add(m)) {
                        continue;
                    }
                    LocalTime slotTime = LocalTime.of(m / 60, m % 60);
                    LocalDateTime start = LocalDateTime.of(date, slotTime);
                    LocalDateTime end = start.plusMinutes(effectiveDuration);
                    boolean withinNormal = fitsAnyWindow(normalWindows, start, end);
                    boolean inPast = start.isBefore(now);
                    candidateSlots.add(new CandidateSlot(slotTime.toString(), start, end, withinNormal, inPast));
                }
            }
            candidateSlots.sort(Comparator.comparing(CandidateSlot::getStartTime));
            return candidateSlots;
        }

        private Map<String, Object> evaluateCandidateSlot(
                CandidateSlot candidate,
                LocalDate date,
                LocalDateTime preferredDateTime,
                List<ExistingBookingFact> existingFacts,
                boolean includeOutsideHours
        ) {
            boolean overlaps = existingFacts.stream()
                    .anyMatch(f -> f.overlaps(candidate.getStartTime(), candidate.getEndTime()));
            boolean feasible = !overlaps && !candidate.isInPast();

            int hardPenalty = (overlaps ? -100 : 0) + (candidate.isInPast() ? -50 : 0);

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

            String reason = overlaps ? "CONFLICT_OVERLAP"
                    : candidate.isInPast() ? "IN_PAST"
                    : adjacentBonus ? "OPTIMAL_ADJACENT_SLOT"
                    : "AVAILABLE";

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
            return slotInfo;
        }

        private boolean fitsAnyWindow(List<TimeWindowFact> windows, LocalDateTime start, LocalDateTime end) {
            return windows.stream().anyMatch(w -> w.contains(start, end));
        }

        public List<TimeWindowFact> resolveWorkingWindowsForDate(ProviderProfile profile, LocalDate date) {
            String raw = profile != null ? profile.getWorkingHoursJson() : null;
            if (raw == null || raw.isBlank()) {
                return List.of(new TimeWindowFact("default-0", 8 * 60, 17 * 60));
            }
            String trimmed = raw.trim();
            if (trimmed.startsWith("{")) {
                List<TimeWindowFact> structured = tryParseStructuredSchedule(trimmed, date);
                if (structured != null) {
                    return structured;
                }
            }
            List<TimeWindowFact> regexWindows = parseRegexWindows(trimmed);
            return regexWindows.isEmpty()
                    ? List.of(new TimeWindowFact("default-0", 8 * 60, 17 * 60))
                    : regexWindows;
        }

        private List<TimeWindowFact> tryParseStructuredSchedule(String json, LocalDate date) {
            try {
                JsonNode root = OBJECT_MAPPER.readTree(json);
                JsonNode days = root.get("days");
                if (days == null || !days.isArray()) {
                    return null;
                }
                String targetDay = date.getDayOfWeek().name();
                List<TimeWindowFact> result = new ArrayList<>();
                for (JsonNode dayNode : days) {
                    if (targetDay.equalsIgnoreCase(dayNode.path("day").asText())) {
                        if (!dayNode.path("enabled").asBoolean(false)) {
                            return List.of();
                        }
                        int idx = 0;
                        for (JsonNode interval : dayNode.path("intervals")) {
                            Integer startMin = parseTimeMinute(interval.path("start").asText(""), false);
                            Integer endMin = parseTimeMinute(interval.path("end").asText(""), true);
                            if (startMin != null && endMin != null && endMin > startMin) {
                                result.add(new TimeWindowFact("win-" + idx++, startMin, endMin));
                            }
                        }
                        return result;
                    }
                }
                return List.of();
            } catch (Exception ignored) {
                return null;
            }
        }

        private List<TimeWindowFact> parseRegexWindows(String text) {
            List<TimeWindowFact> windows = new ArrayList<>();
            Matcher matcher = HOURS_RANGE_PATTERN.matcher(text);
            int idx = 0;
            while (matcher.find()) {
                Integer startMin = parseTimeMinute(matcher.group(1), false);
                Integer endMin = parseTimeMinute(matcher.group(2), true);
                if (startMin != null && endMin != null && endMin > startMin) {
                    windows.add(new TimeWindowFact("win-" + idx++, startMin, endMin));
                }
            }
            return windows;
        }

        private static Integer parseTimeMinute(String raw, boolean isEnd) {
            String trimmed = raw.trim();
            if ("24:00".equals(trimmed) && isEnd) {
                return 1440;
            }
            try {
                LocalTime t = LocalTime.parse(trimmed);
                int mins = t.getHour() * 60 + t.getMinute();
                return (isEnd && mins == 0) ? 1440 : mins;
            } catch (Exception ignored) {
                return null;
            }
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

    // =========================================================================
    // 3. REPOSITORIES & PORTS (Package-Private Repository + Public Port)
    // =========================================================================

    public interface AppointmentPort {
        List<Appointment> findByClientUuid(UUID clientUuid);
        List<Appointment> findByTherapistUuid(UUID therapistUuid);
        List<Appointment> findAll();
        Optional<Appointment> findAppointmentById(UUID id);
        Appointment saveAppointment(Appointment appointment);
        void linkJournalEntry(UUID appointmentId, UUID journalEntryId);
    }

    @Repository
    interface AppointmentRepository extends BaseRepository<Appointment>, AppointmentPort {
        @Override
        List<Appointment> findByClientUuid(UUID clientUuid);

        @Override
        List<Appointment> findByTherapistUuid(UUID therapistUuid);

        boolean existsByStartTime(LocalDateTime startTime);

        @Override
        default Optional<Appointment> findAppointmentById(UUID id) {
            return findById(id);
        }

        @Override
        default Appointment saveAppointment(Appointment appointment) {
            return save(appointment);
        }

        @Override
        default void linkJournalEntry(UUID appointmentId, UUID journalEntryId) {
            findById(appointmentId).ifPresent(apt -> {
                apt.setJournalEntryId(journalEntryId);
                save(apt);
            });
        }
    }

    // =========================================================================
    // 4. DOMAIN SERVICES (Package-Private)
    // =========================================================================

    @Service
    static class AppointmentService extends BaseService<Appointment, AppointmentDto> {

        private static final Logger log = LoggerFactory.getLogger(AppointmentService.class);

        private final AppointmentRepository appointmentRepository;
        private final PlatformCacheService cacheService;
        private final PlatformEventService eventService;
        private final TimefoldBookingService timefoldBookingService;
        private final ProviderProfilePort providerProfileRepository;
        private final JournalGitService journalGitService;
        private final ServiceLookupPort serviceLookupPort;

        AppointmentService(AppointmentRepository appointmentRepository) {
            this(appointmentRepository, new PlatformCacheService(), new PlatformEventService(), new TimefoldBookingService(), null, null, null);
        }

        AppointmentService(
                AppointmentRepository appointmentRepository,
                PlatformCacheService cacheService,
                PlatformEventService eventService
        ) {
            this(appointmentRepository, cacheService, eventService, new TimefoldBookingService(), null, null, null);
        }

        AppointmentService(
                AppointmentRepository appointmentRepository,
                PlatformCacheService cacheService,
                PlatformEventService eventService,
                TimefoldBookingService timefoldBookingService,
                ProviderProfilePort providerProfileRepository
        ) {
            this(appointmentRepository, cacheService, eventService, timefoldBookingService, providerProfileRepository, null, null);
        }

        AppointmentService(
                AppointmentRepository appointmentRepository,
                PlatformCacheService cacheService,
                PlatformEventService eventService,
                TimefoldBookingService timefoldBookingService,
                ProviderProfilePort providerProfileRepository,
                JournalGitService journalGitService
        ) {
            this(appointmentRepository, cacheService, eventService, timefoldBookingService, providerProfileRepository, journalGitService, null);
        }

        @Autowired
        AppointmentService(
                AppointmentRepository appointmentRepository,
                PlatformCacheService cacheService,
                PlatformEventService eventService,
                TimefoldBookingService timefoldBookingService,
                @Autowired(required = false) ProviderProfilePort providerProfileRepository,
                @Autowired(required = false) JournalGitService journalGitService,
                @Autowired(required = false) ServiceLookupPort serviceLookupPort
        ) {
            super(appointmentRepository);
            this.appointmentRepository = appointmentRepository;
            this.cacheService = Objects.requireNonNullElseGet(cacheService, PlatformCacheService::new);
            this.eventService = Objects.requireNonNullElseGet(eventService, PlatformEventService::new);
            this.timefoldBookingService = Objects.requireNonNullElseGet(timefoldBookingService, TimefoldBookingService::new);
            this.providerProfileRepository = providerProfileRepository;
            this.journalGitService = journalGitService;
            this.serviceLookupPort = serviceLookupPort;
        }

        @Override
        protected AppointmentDto toDto(Appointment appointmentEntity) {
            if (appointmentEntity == null) {
                return null;
            }
            return new AppointmentDto(
                    appointmentEntity.getId(),
                    appointmentEntity.getClientUuid(),
                    appointmentEntity.getTherapistUuid(),
                    appointmentEntity.getServiceId(),
                    appointmentEntity.getClientName(),
                    appointmentEntity.getTherapistName(),
                    appointmentEntity.getOrganisationName(),
                    appointmentEntity.getLocation(),
                    appointmentEntity.getStartTime(),
                    appointmentEntity.getEndTime(),
                    appointmentEntity.getTitle(),
                    appointmentEntity.getStatus(),
                    appointmentEntity.getGitCommitHash(),
                    appointmentEntity.getJournalEntryId()
            );
        }

        @Override
        protected Appointment toEntity(AppointmentDto appointmentDto) {
            if (appointmentDto == null) {
                return null;
            }
            Appointment appointmentEntity = new Appointment(
                    appointmentDto.clientUuid(),
                    appointmentDto.therapistUuid(),
                    appointmentDto.startTime(),
                    appointmentDto.endTime(),
                    appointmentDto.title(),
                    appointmentDto.status()
            );
            appointmentEntity.setId(appointmentDto.id());
            appointmentEntity.setServiceId(appointmentDto.serviceId());
            appointmentEntity.setClientName(appointmentDto.clientName());
            appointmentEntity.setTherapistName(appointmentDto.therapistName());
            appointmentEntity.setOrganisationName(appointmentDto.organisationName());
            appointmentEntity.setLocation(appointmentDto.location());
            appointmentEntity.setGitCommitHash(appointmentDto.gitCommitHash());
            appointmentEntity.setJournalEntryId(appointmentDto.journalEntryId());
            return appointmentEntity;
        }

        private void validateNoSelfBooking(UUID clientUuid, UUID therapistUuid) {
            ErrorCode.BAD_REQUEST.require(
                    clientUuid == null || therapistUuid == null || !Objects.equals(clientUuid, therapistUuid),
                    "A therapist cannot book themselves as their own therapist"
            );
        }

        private LocalDateTime resolveEndTime(
                LocalDateTime startTime,
                LocalDateTime endTime,
                UUID serviceId,
                int fallbackMinutes
        ) {
            if (startTime == null) {
                return endTime;
            }
            if (endTime != null) {
                ErrorCode.BAD_REQUEST.require(
                        endTime.isAfter(startTime),
                        "Invalid time range: End time must be after start time"
                );
                return endTime;
            }
            int duration = (serviceId != null && serviceLookupPort != null)
                    ? serviceLookupPort.findServiceById(serviceId)
                            .map(com.platform.features.clinic.ClinicPlatformFeature.Service::getDurationMinutes)
                            .filter(d -> d > 0)
                            .orElse(fallbackMinutes)
                    : fallbackMinutes;
            return startTime.plusMinutes(duration);
        }

        private boolean shouldEnforceWorkingHours(UUID therapistUuid) {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.getPrincipal() instanceof Jwt jwt && jwt.getSubject() != null) {
                return !therapistUuid.toString().equals(jwt.getSubject().trim());
            }
            return false;
        }

        @Override
        protected void validateBeforeCreate(AppointmentDto dto) {
            validateNoSelfBooking(dto.clientUuid(), dto.therapistUuid());
            if (dto.therapistUuid() == null && dto.startTime() != null) {
                ErrorCode.CONFLICT.require(
                        !appointmentRepository.existsByStartTime(dto.startTime()),
                        "Tidspunktet " + dto.startTime() + " er allerede booket."
                );
            }
        }

        @Override
        @Transactional
        public AppointmentDto create(AppointmentDto dto) {
            validateBeforeCreate(dto);
            return toDto(createAppointment(toEntity(dto)));
        }

        @Override
        @Transactional
        public AppointmentDto update(UUID id, AppointmentDto dto) {
            validateBeforeUpdate(id, dto);
            return toDto(rescheduleAppointment(id, toEntity(dto)));
        }

        @Override
        @Transactional
        public void deleteById(UUID id) {
            cancelAppointment(id);
        }

        public List<Appointment> getAppointmentsForClient(UUID clientUuid) {
            return getAppointmentsForClient(clientUuid, null, null);
        }

        public List<Appointment> getAppointmentsForClient(UUID clientUuid, LocalDate fromDate, LocalDate toDate) {
            String cacheKey = buildClientCacheKey(clientUuid, fromDate, toDate);
            List<Appointment> cached = cacheService.get(cacheKey);
            if (cached != null) {
                return cached;
            }

            List<Appointment> appointments = appointmentRepository.findByClientUuid(clientUuid);
            if (fromDate != null && toDate != null) {
                LocalDateTime startBoundary = fromDate.atStartOfDay();
                LocalDateTime endBoundary = toDate.plusDays(1).atStartOfDay();
                appointments = appointments.stream()
                        .filter(a -> a.getStartTime() != null
                                && !a.getStartTime().isBefore(startBoundary)
                                && a.getStartTime().isBefore(endBoundary))
                        .collect(Collectors.toList());
            }

            cacheService.put(cacheKey, appointments);
            return appointments;
        }

        public List<Appointment> getAppointmentsForTherapist(UUID therapistUuid) {
            return appointmentRepository.findByTherapistUuid(therapistUuid);
        }

        public Map<String, Object> getTimefoldSlotSuggestions(
                UUID therapistUuid,
                LocalDate date,
                int durationMinutes,
                LocalTime preferredTime,
                UUID excludeAppointmentId
        ) {
            return getTimefoldSlotSuggestions(
                    therapistUuid, date, durationMinutes, preferredTime, excludeAppointmentId, false
            );
        }

        public Map<String, Object> getTimefoldSlotSuggestions(
                UUID therapistUuid,
                LocalDate date,
                int durationMinutes,
                LocalTime preferredTime,
                UUID excludeAppointmentId,
                boolean includeOutsideHours
        ) {
            List<Appointment> existingAppointments = appointmentRepository.findByTherapistUuid(therapistUuid);
            ProviderProfile profile = providerProfileRepository != null
                    ? providerProfileRepository.findByUserUuid(therapistUuid).orElse(null)
                    : null;
            return timefoldBookingService.suggestOptimalSlots(
                    therapistUuid,
                    date,
                    durationMinutes,
                    preferredTime,
                    existingAppointments,
                    profile,
                    excludeAppointmentId,
                    includeOutsideHours
            );
        }

        @Transactional
        public Appointment createAppointment(Appointment appointment) {
            // Trin 1: Valider selv-booking samt kronologisk interval (eller udled sluttid fra ydelse)
            validateNoSelfBooking(appointment.getClientUuid(), appointment.getTherapistUuid());
            appointment.setEndTime(resolveEndTime(
                    appointment.getStartTime(),
                    appointment.getEndTime(),
                    appointment.getServiceId(),
                    60
            ));

            // Trin 2: Lad Timefold Solver nægte bookingen ved overlap eller ugyldig arbejdstid for klient
            if (appointment.getTherapistUuid() != null) {
                List<Appointment> existingAppointments = appointmentRepository.findByTherapistUuid(appointment.getTherapistUuid());
                ProviderProfile profile = providerProfileRepository != null
                        ? providerProfileRepository.findByUserUuid(appointment.getTherapistUuid()).orElse(null)
                        : null;
                timefoldBookingService.assertBookingAllowed(
                        appointment.getTherapistUuid(),
                        appointment.getStartTime(),
                        appointment.getEndTime(),
                        existingAppointments,
                        profile,
                        null,
                        shouldEnforceWorkingHours(appointment.getTherapistUuid())
                );
            }

            // Trin 3: Persister aftalen med status `CONFIRMED` og skriv SHA-1 revisionscommit i Eclipse JGit
            appointment.setStatus("CONFIRMED");
            Appointment saved = appointmentRepository.save(appointment);

            if (saved != null) {
                if (journalGitService != null && saved.getClientUuid() != null) {
                    try {
                        String title = Objects.requireNonNullElse(saved.getTitle(), "Aftale");
                        String author = Objects.requireNonNullElse(saved.getTherapistName(), "Klinik Booking");
                        String content = "## Aftale: " + title + "\n"
                                + "Tidspunkt: " + saved.getStartTime() + " - " + saved.getEndTime() + "\n"
                                + "Status: " + saved.getStatus() + "\n"
                                + "Terapeut: " + Objects.requireNonNullElse(saved.getTherapistName(), "-")
                                + " (" + Objects.requireNonNullElse(saved.getOrganisationName(), "-") + ")\n"
                                + (saved.getLocation() != null ? "Lokation: " + saved.getLocation() + "\n" : "")
                                + "Refs-Appointment: #" + saved.getId();
                        String commitMsg = "Aftale oprettet: " + title + " (" + saved.getStartTime() + ")";
                        String hash = journalGitService.commitJournalEntryWithHash(
                                saved.getClientUuid().toString(),
                                content,
                                commitMsg,
                                author
                        );
                        saved.setGitCommitHash(hash);
                        saved = appointmentRepository.save(saved);
                    } catch (Exception e) {
                        log.warn("Failed to commit appointment to Git for client {}", saved.getClientUuid(), e);
                    }
                }

                if (saved.getClientUuid() != null) {
                    cacheService.evictByPrefix("appointments:client:" + saved.getClientUuid());
                }
                if (saved.getId() != null) {
                    cacheService.put("appointment:" + saved.getId(), saved);
                }
                eventService.publishEvent(
                        "appointment-events",
                        "CREATED",
                        saved.getId() != null ? saved.getId().toString() : "",
                        Map.of(
                                "status", "CONFIRMED",
                                "clientUuid", String.valueOf(saved.getClientUuid()),
                                "therapistUuid", String.valueOf(saved.getTherapistUuid()),
                                "gitCommitHash", String.valueOf(saved.getGitCommitHash())
                        )
                );
            }
            return saved;
        }

        @Transactional
        public Appointment rescheduleAppointment(UUID id, Appointment updated) {
            Appointment existing = appointmentRepository.findById(id)
                    .orElseThrow(ErrorCode.NOT_FOUND.supply("Appointment not found"));

            LocalDateTime now = LocalDateTime.now();
            ErrorCode.BAD_REQUEST.require(
                    existing.getStartTime() == null || !existing.getStartTime().isBefore(now),
                    "Cannot reschedule an appointment that is in the past"
            );
            ErrorCode.BAD_REQUEST.require(
                    updated.getStartTime() == null || !updated.getStartTime().isBefore(now),
                    "Cannot reschedule an appointment to a time in the past"
            );

            int existingDurationMinutes = (existing.getStartTime() != null && existing.getEndTime() != null)
                    ? (int) Math.max(15L, Duration.between(existing.getStartTime(), existing.getEndTime()).toMinutes())
                    : 60;
            LocalDateTime resolvedEnd = resolveEndTime(
                    updated.getStartTime(),
                    updated.getEndTime(),
                    existing.getServiceId(),
                    existingDurationMinutes
            );

            if (existing.getTherapistUuid() != null && updated.getStartTime() != null) {
                List<Appointment> therapistAppointments = appointmentRepository.findByTherapistUuid(existing.getTherapistUuid());
                ProviderProfile profile = providerProfileRepository != null
                        ? providerProfileRepository.findByUserUuid(existing.getTherapistUuid()).orElse(null)
                        : null;
                timefoldBookingService.assertBookingAllowed(
                        existing.getTherapistUuid(),
                        updated.getStartTime(),
                        resolvedEnd,
                        therapistAppointments,
                        profile,
                        id,
                        shouldEnforceWorkingHours(existing.getTherapistUuid())
                );
            }

            LocalDateTime previousStartTime = existing.getStartTime();
            String previousHash = existing.getGitCommitHash();

            existing.setStartTime(updated.getStartTime());
            existing.setEndTime(resolvedEnd);
            if (updated.getClientName() != null) {
                existing.setClientName(updated.getClientName());
            }
            if (updated.getTitle() != null) {
                existing.setTitle(updated.getTitle());
            }
            if (updated.getLocation() != null && !updated.getLocation().isBlank()) {
                existing.setLocation(updated.getLocation());
            }
            existing.setStatus("CONFIRMED");

            if (journalGitService != null && existing.getClientUuid() != null) {
                try {
                    String title = Objects.requireNonNullElse(existing.getTitle(), "Aftale");
                    String author = Objects.requireNonNullElse(existing.getTherapistName(), "Klinik Booking");
                    String content = "## Aftale opdateret / ombooket: " + title + "\n"
                            + "Nyt tidspunkt: " + existing.getStartTime() + " - " + existing.getEndTime() + "\n"
                            + "Tidligere tidspunkt: " + previousStartTime + "\n"
                            + "Status: " + existing.getStatus() + "\n"
                            + "Terapeut: " + Objects.requireNonNullElse(existing.getTherapistName(), "-")
                            + " (" + Objects.requireNonNullElse(existing.getOrganisationName(), "-") + ")\n"
                            + (existing.getLocation() != null ? "Lokation: " + existing.getLocation() + "\n" : "")
                            + "Refs-Appointment: #" + existing.getId() + "\n"
                            + (previousHash != null ? "Refs-Commit: #" + previousHash : "");
                    String commitMsg = "Aftale ombooket: " + title + " til " + existing.getStartTime();
                    String hash = journalGitService.commitJournalEntryWithHash(
                            existing.getClientUuid().toString(),
                            content,
                            commitMsg,
                            author
                    );
                    existing.setGitCommitHash(hash);
                } catch (Exception e) {
                    log.warn("Failed to commit appointment reschedule to Git for client {}", existing.getClientUuid(), e);
                }
            }

            Appointment saved = appointmentRepository.save(existing);
            if (existing.getClientUuid() != null) {
                cacheService.evictByPrefix("appointments:client:" + existing.getClientUuid());
            }
            cacheService.put("appointment:" + id, saved);
            return saved;
        }

        @Transactional
        public void cancelAppointment(UUID id) {
            Appointment appointment = appointmentRepository.findById(id)
                    .orElseThrow(ErrorCode.NOT_FOUND.supply("Appointment not found"));

            ErrorCode.BAD_REQUEST.require(
                    appointment.getStartTime() == null || !appointment.getStartTime().isBefore(LocalDateTime.now()),
                    "Cannot cancel an appointment that is in the past"
            );

            appointment.setStatus("CANCELLED");

            if (journalGitService != null && appointment.getClientUuid() != null) {
                try {
                    String title = Objects.requireNonNullElse(appointment.getTitle(), "Aftale");
                    String author = Objects.requireNonNullElse(appointment.getTherapistName(), "Klinik Booking");
                    String content = "## Aftale aflyst: " + title + "\n"
                            + "Tidligere tidspunkt: " + appointment.getStartTime() + " - " + appointment.getEndTime() + "\n"
                            + "Status: CANCELLED\n"
                            + "Terapeut: " + Objects.requireNonNullElse(appointment.getTherapistName(), "-")
                            + " (" + Objects.requireNonNullElse(appointment.getOrganisationName(), "-") + ")\n"
                            + (appointment.getLocation() != null ? "Lokation: " + appointment.getLocation() + "\n" : "")
                            + "Refs-Appointment: #" + appointment.getId() + "\n"
                            + (appointment.getGitCommitHash() != null ? "Refs-Commit: #" + appointment.getGitCommitHash() : "");
                    String commitMsg = "Aftale aflyst: " + title;
                    String hash = journalGitService.commitJournalEntryWithHash(
                            appointment.getClientUuid().toString(),
                            content,
                            commitMsg,
                            author
                    );
                    appointment.setGitCommitHash(hash);
                } catch (Exception e) {
                    log.warn("Failed to commit appointment cancellation to Git for client {}", appointment.getClientUuid(), e);
                }
            }

            appointmentRepository.save(appointment);
            cacheService.evict("appointment:" + id);
            if (appointment.getClientUuid() != null) {
                cacheService.evictByPrefix("appointments:client:" + appointment.getClientUuid());
            }
            eventService.publishEvent(
                    "appointment-events",
                    "CANCELLED",
                    id.toString(),
                    Map.of(
                            "status", "CANCELLED",
                            "gitCommitHash", String.valueOf(appointment.getGitCommitHash())
                    )
            );
        }

        public List<Map<String, String>> getAppointmentGitHistory(UUID appointmentId) {
            Appointment apt = appointmentRepository.findById(appointmentId)
                    .orElseThrow(ErrorCode.NOT_FOUND.supply("Appointment not found"));
            if (journalGitService == null || apt.getClientUuid() == null) {
                return List.of();
            }
            String refTag = "Refs-Appointment: #" + appointmentId;
            String aptIdStr = appointmentId.toString();
            List<Map<String, String>> clientHistory = journalGitService.getCommitHistory(
                    apt.getClientUuid().toString(),
                    false,
                    JournalFeature::filterInternalNotes
            );
            return clientHistory.stream()
                    .filter(commit -> {
                        String msg = commit.getOrDefault("message", "");
                        String snapshot = commit.getOrDefault("snapshot", "");
                        return msg.contains(aptIdStr) || snapshot.contains(refTag) || snapshot.contains(aptIdStr);
                    })
                    .collect(Collectors.toList());
        }

        public String buildClientCacheKey(UUID clientUuid, LocalDate fromDate, LocalDate toDate) {
            if (fromDate != null && toDate != null) {
                return "appointments:client:" + clientUuid + ":" + fromDate + ":" + toDate;
            }
            return "appointments:client:" + clientUuid + ":all";
        }
    }

    // =========================================================================
    // 5. REST CONTROLLERS (Package-Private)
    // =========================================================================

    @RestController
    @RequestMapping("/api/v1/appointments")
    @CrossOrigin(origins = "*")
    static class AppointmentController extends BaseController<Appointment, AppointmentDto> {

        private final AppointmentService appointmentService;

        AppointmentController(AppointmentService appointmentService) {
            super(appointmentService);
            this.appointmentService = appointmentService;
        }

        public ResponseEntity<List<AppointmentDto>> getAppointmentsForClient(UUID clientUuid) {
            return getAppointmentsForClient(clientUuid, null, null);
        }

        @GetMapping("/client/{clientUuid}")
        public ResponseEntity<List<AppointmentDto>> getAppointmentsForClient(
                @PathVariable UUID clientUuid,
                @RequestParam(required = false) String from,
                @RequestParam(required = false) String to
        ) {
            LocalDate fromDate = (from != null && !from.isBlank()) ? LocalDate.parse(from) : null;
            LocalDate toDate = (to != null && !to.isBlank()) ? LocalDate.parse(to) : null;
            List<AppointmentDto> dtos = appointmentService.getAppointmentsForClient(clientUuid, fromDate, toDate)
                    .stream()
                    .map(appointmentService::toDto)
                    .collect(Collectors.toList());
            return ResponseEntity.ok(dtos);
        }

        @GetMapping("/therapist/{therapistUuid}")
        public ResponseEntity<List<AppointmentDto>> getAppointmentsForTherapist(@PathVariable UUID therapistUuid) {
            List<AppointmentDto> dtos = appointmentService.getAppointmentsForTherapist(therapistUuid)
                    .stream()
                    .map(appointmentService::toDto)
                    .collect(Collectors.toList());
            return ResponseEntity.ok(dtos);
        }

        public ResponseEntity<Map<String, Object>> getTimefoldOptimalSlots(
                UUID therapistUuid,
                String date,
                int durationMinutes,
                String preferredTime,
                UUID excludeAppointmentId
        ) {
            return getTimefoldOptimalSlots(therapistUuid, date, durationMinutes, preferredTime, excludeAppointmentId, false);
        }

        @GetMapping("/timefold-slots")
        public ResponseEntity<Map<String, Object>> getTimefoldOptimalSlots(
                @RequestParam UUID therapistUuid,
                @RequestParam String date,
                @RequestParam(required = false, defaultValue = "60") int durationMinutes,
                @RequestParam(required = false) String preferredTime,
                @RequestParam(required = false) UUID excludeAppointmentId,
                @RequestParam(required = false, defaultValue = "false") boolean includeOutsideHours
        ) {
            LocalDate parsedDate = LocalDate.parse(date);
            LocalTime parsedPreferred = (preferredTime != null && !preferredTime.isBlank())
                    ? LocalTime.parse(preferredTime.length() == 5 ? preferredTime : preferredTime.substring(0, 5))
                    : LocalTime.of(10, 0);
            return ResponseEntity.ok(appointmentService.getTimefoldSlotSuggestions(
                    therapistUuid,
                    parsedDate,
                    durationMinutes,
                    parsedPreferred,
                    excludeAppointmentId,
                    includeOutsideHours
            ));
        }

        @Override
        @PostMapping
        public ResponseEntity<AppointmentDto> create(@RequestBody AppointmentDto dto) {
            boolean requestedScheduled = "SCHEDULED".equalsIgnoreCase(dto.status())
                    || dto.serviceId() != null
                    || (dto.clientName() != null && !dto.clientName().isBlank());
            AppointmentDto created = appointmentService.create(dto);
            if (requestedScheduled) {
                return ResponseEntity.status(HttpStatus.CREATED).body(created);
            }
            return ResponseEntity.ok(created);
        }

        public ResponseEntity<AppointmentDto> createAppointment(Appointment appointment) {
            return create(appointmentService.toDto(appointment));
        }

        @Override
        @PutMapping("/{id}")
        public ResponseEntity<AppointmentDto> update(@PathVariable UUID id, @RequestBody AppointmentDto updated) {
            return ResponseEntity.ok(appointmentService.update(id, updated));
        }

        public ResponseEntity<AppointmentDto> rescheduleAppointment(UUID id, Appointment updated) {
            return update(id, appointmentService.toDto(updated));
        }

        @Override
        @DeleteMapping("/{id}")
        public ResponseEntity<Void> delete(@PathVariable UUID id) {
            return cancelAppointment(id);
        }

        public ResponseEntity<Void> cancelAppointment(UUID id) {
            appointmentService.deleteById(id);
            return ResponseEntity.ok().build();
        }

        @GetMapping("/{id}/git-history")
        public ResponseEntity<List<Map<String, String>>> getAppointmentGitHistory(@PathVariable UUID id) {
            return ResponseEntity.ok(appointmentService.getAppointmentGitHistory(id));
        }
    }
}
