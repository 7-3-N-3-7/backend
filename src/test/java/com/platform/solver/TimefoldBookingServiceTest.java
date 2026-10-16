package com.platform.solver;

import ai.timefold.solver.core.api.score.buildin.hardsoft.HardSoftScore;
import com.platform.entity.Appointment;
import com.platform.entity.ProviderProfile;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class TimefoldBookingServiceTest {

    private final TimefoldBookingService solverService = new TimefoldBookingService();

    @Test
    @SuppressWarnings("unchecked")
    void suggestOptimalSlotsRewardsAdjacentAndAvoidsOverlaps() {
        UUID therapistUuid = UUID.randomUUID();
        LocalDate futureDate = LocalDate.now().plusDays(10);

        Appointment existing10to11 = new Appointment();
        existing10to11.setId(UUID.randomUUID());
        existing10to11.setTherapistUuid(therapistUuid);
        existing10to11.setStartTime(LocalDateTime.of(futureDate, LocalTime.of(10, 0)));
        existing10to11.setEndTime(LocalDateTime.of(futureDate, LocalTime.of(11, 0)));
        existing10to11.setStatus("SCHEDULED");

        Appointment cancelled11to12 = new Appointment();
        cancelled11to12.setId(UUID.randomUUID());
        cancelled11to12.setTherapistUuid(therapistUuid);
        cancelled11to12.setStartTime(LocalDateTime.of(futureDate, LocalTime.of(11, 0)));
        cancelled11to12.setEndTime(LocalDateTime.of(futureDate, LocalTime.of(12, 0)));
        cancelled11to12.setStatus("CANCELLED");

        ProviderProfile profile = new ProviderProfile();
        profile.setWorkingHoursJson("{\"monday\":\"09:00-15:00\"}");

        Map<String, Object> result = solverService.suggestOptimalSlots(
                therapistUuid,
                futureDate,
                60,
                LocalTime.of(11, 0),
                List.of(existing10to11, cancelled11to12),
                profile,
                null
        );

        assertEquals("Timefold Solver 1.16.0", result.get("solver"));
        assertEquals("11:00", result.get("recommendedSlot"));
        assertEquals(Boolean.TRUE, result.get("feasible"));
        assertEquals("09:00-15:00", result.get("workingHoursWindow"));

        List<Map<String, Object>> slots = (List<Map<String, Object>>) result.get("slots");
        assertEquals(6, slots.size());

        Map<String, Object> slot10 = slots.stream().filter(s -> "10:00".equals(s.get("slot"))).findFirst().orElseThrow();
        assertEquals(Boolean.FALSE, slot10.get("available"));
        assertEquals("CONFLICT_OVERLAP", slot10.get("reason"));

        Map<String, Object> slot11 = slots.stream().filter(s -> "11:00".equals(s.get("slot"))).findFirst().orElseThrow();
        assertEquals(Boolean.TRUE, slot11.get("available"));
        assertEquals("OPTIMAL_ADJACENT_SLOT", slot11.get("reason"));
        assertEquals(Boolean.TRUE, slot11.get("recommended"));

        Map<String, Object> slot15 = slots.stream().filter(s -> "15:00".equals(s.get("slot"))).findFirst().orElseThrow();
        assertEquals(Boolean.FALSE, slot15.get("available"));
        assertEquals("OUTSIDE_WORKING_HOURS", slot15.get("reason"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void suggestOptimalSlotsHandlesPastDateAndExclusions() {
        UUID therapistUuid = UUID.randomUUID();
        LocalDate pastDate = LocalDate.now().minusDays(5);
        UUID excludedId = UUID.randomUUID();

        Appointment excludedApt = new Appointment();
        excludedApt.setId(excludedId);
        excludedApt.setTherapistUuid(therapistUuid);
        excludedApt.setStartTime(LocalDateTime.of(pastDate, LocalTime.of(10, 0)));
        excludedApt.setEndTime(LocalDateTime.of(pastDate, LocalTime.of(11, 0)));
        excludedApt.setStatus("SCHEDULED");

        Map<String, Object> result = solverService.suggestOptimalSlots(
                therapistUuid,
                pastDate,
                0,
                null,
                List.of(excludedApt),
                null,
                excludedId
        );

        assertEquals(Boolean.FALSE, result.get("feasible"));
        assertNull(result.get("recommendedSlot"));
        List<Map<String, Object>> slots = (List<Map<String, Object>>) result.get("slots");
        for (Map<String, Object> slot : slots) {
            assertEquals("IN_PAST", slot.get("reason"));
            assertEquals(Boolean.FALSE, slot.get("available"));
        }
    }

    @Test
    void parseWorkingWindowHandlesVariousInputs() {
        LocalTime[] defaultWindow = solverService.parseWorkingWindow(null);
        assertEquals(LocalTime.of(8, 0), defaultWindow[0]);
        assertEquals(LocalTime.of(17, 0), defaultWindow[1]);

        LocalTime[] blankWindow = solverService.parseWorkingWindow("   ");
        assertEquals(LocalTime.of(8, 0), blankWindow[0]);

        LocalTime[] invalidOrder = solverService.parseWorkingWindow("16:00-09:00");
        assertEquals(LocalTime.of(8, 0), invalidOrder[0]);

        LocalTime[] invalidTime = solverService.parseWorkingWindow("99:99-88:88");
        assertEquals(LocalTime.of(8, 0), invalidTime[0]);

        LocalTime[] validWindow = solverService.parseWorkingWindow("08:30 - 16:30");
        assertEquals(LocalTime.of(8, 30), validWindow[0]);
        assertEquals(LocalTime.of(16, 30), validWindow[1]);
    }

    @Test
    void domainModelConstructorsAndGettersWork() {
        TimefoldBookingService.CandidateSlot emptySlot = new TimefoldBookingService.CandidateSlot();
        assertNull(emptySlot.getId());

        TimefoldBookingService.ExistingBookingFact emptyFact = new TimefoldBookingService.ExistingBookingFact();
        assertNull(emptyFact.getId());

        TimefoldBookingService.ExistingBookingFact autoIdFact = new TimefoldBookingService.ExistingBookingFact(
                null, UUID.randomUUID(), LocalDateTime.now(), LocalDateTime.now().plusHours(1)
        );
        assertNotNull(autoIdFact.getId());
        assertNotNull(autoIdFact.getStartTime());
        assertNotNull(autoIdFact.getEndTime());

        TimefoldBookingService.BookingAssignment emptyAssignment = new TimefoldBookingService.BookingAssignment();
        assertEquals(0, emptyAssignment.getDurationMinutes());

        UUID assignmentId = UUID.randomUUID();
        UUID therapistId = UUID.randomUUID();
        TimefoldBookingService.BookingAssignment fullAssignment = new TimefoldBookingService.BookingAssignment(
                assignmentId, therapistId, 45, LocalDateTime.now(), emptySlot
        );
        assertEquals(assignmentId, fullAssignment.getId());
        assertEquals(therapistId, fullAssignment.getTherapistUuid());
        assertEquals(45, fullAssignment.getDurationMinutes());

        TimefoldBookingService.TherapistScheduleSolution emptySolution = new TimefoldBookingService.TherapistScheduleSolution();
        emptySolution.setScore(HardSoftScore.ZERO);
        assertEquals(HardSoftScore.ZERO, emptySolution.getScore());
        assertNull(emptySolution.getCandidateSlots());
        assertNull(emptySolution.getExistingBookings());
    }
}
