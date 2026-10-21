package com.platform.service;

import com.platform.controller.AppointmentController;
import com.platform.entity.Appointment;
import com.platform.repository.AppointmentRepository;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;

public class AppointmentServiceTest {

    private AppointmentRepository appointmentRepository;
    private AppointmentService appointmentService;
    private AppointmentController appointmentController;

    @BeforeEach
    public void setUp() {
        appointmentRepository = Mockito.mock(AppointmentRepository.class);
        appointmentService = new AppointmentService(appointmentRepository);
        appointmentController = new AppointmentController(appointmentService);
    }

    @Test
    public void testCreateAppointmentAndMetadata() {
        UUID clientUuid = UUID.randomUUID();
        UUID therapistUuid = UUID.randomUUID();
        UUID serviceId = UUID.randomUUID();
        Appointment appointment = new Appointment(clientUuid, therapistUuid, LocalDateTime.now(), LocalDateTime.now().plusHours(1), "Initial Assessment", "PENDING");
        appointment.setId(UUID.randomUUID());
        appointment.setServiceId(serviceId);
        appointment.setTherapistName("Dr. Maja Holm");
        appointment.setOrganisationName("Nordic Mind");
        appointment.setLocation("København K");

        Mockito.when(appointmentRepository.findByTherapistUuid(therapistUuid)).thenReturn(List.of());
        Mockito.when(appointmentRepository.save(any(Appointment.class))).thenReturn(appointment);

        Appointment saved = appointmentController.createAppointment(appointment).getBody();
        Assertions.assertNotNull(saved.getId());
        Assertions.assertEquals("CONFIRMED", saved.getStatus());
        Assertions.assertEquals(serviceId, saved.getServiceId());
        Assertions.assertEquals("Dr. Maja Holm", saved.getTherapistName());
        Assertions.assertEquals("Nordic Mind", saved.getOrganisationName());
        Assertions.assertEquals("København K", saved.getLocation());
    }

    @Test
    public void testFindByClientAndTherapistUuid() {
        UUID clientUuid = UUID.randomUUID();
        UUID therapistUuid = UUID.randomUUID();
        Appointment app1 = new Appointment(clientUuid, therapistUuid, LocalDateTime.now(), LocalDateTime.now().plusHours(1), "Session 1", "CONFIRMED");
        
        Mockito.when(appointmentRepository.findByClientUuid(clientUuid)).thenReturn(List.of(app1));
        Mockito.when(appointmentRepository.findByTherapistUuid(therapistUuid)).thenReturn(List.of(app1));

        Assertions.assertEquals(1, appointmentController.getAppointmentsForClient(clientUuid).getBody().size());
        Assertions.assertEquals(1, appointmentController.getAppointmentsForTherapist(therapistUuid).getBody().size());
    }

    @Test
    public void testRescheduleAndCancelAppointment() {
        UUID appId = UUID.randomUUID();
        UUID clientUuid = UUID.randomUUID();
        UUID therapistUuid = UUID.randomUUID();
        LocalDateTime start = LocalDateTime.now().plusDays(5).withHour(10).withMinute(0).withSecond(0).withNano(0);
        Appointment existing = new Appointment(clientUuid, therapistUuid, start, start.plusHours(1), "Session 1", "CONFIRMED");
        existing.setId(appId);

        Appointment otherBooked = new Appointment(UUID.randomUUID(), therapistUuid, start.plusHours(2), start.plusHours(3), "Other", "CONFIRMED");
        otherBooked.setId(UUID.randomUUID());

        Mockito.when(appointmentRepository.findById(appId)).thenReturn(Optional.of(existing));
        Mockito.when(appointmentRepository.findByTherapistUuid(therapistUuid)).thenReturn(List.of(existing, otherBooked));
        Mockito.when(appointmentRepository.save(any(Appointment.class))).thenAnswer(i -> i.getArgument(0));

        // Valid reschedule
        Appointment updateReq = new Appointment(clientUuid, therapistUuid, start.plusHours(4), start.plusHours(5), "Rescheduled Session", "CONFIRMED");
        Appointment rescheduled = appointmentController.rescheduleAppointment(appId, updateReq).getBody();
        Assertions.assertEquals("Rescheduled Session", rescheduled.getTitle());

        // Conflict on reschedule
        Appointment conflictReq = new Appointment(clientUuid, therapistUuid, start.plusHours(2).plusMinutes(15), start.plusHours(3).plusMinutes(15), "Conflict", "CONFIRMED");
        Assertions.assertThrows(ResponseStatusException.class, () -> appointmentController.rescheduleAppointment(appId, conflictReq));

        // Invalid time range on reschedule
        Appointment invalidRange = new Appointment(clientUuid, therapistUuid, start.plusHours(5), start.plusHours(4), "Invalid", "CONFIRMED");
        Assertions.assertThrows(ResponseStatusException.class, () -> appointmentController.rescheduleAppointment(appId, invalidRange));

        // Reject rescheduling TO a time in the past
        Appointment toPastReq = new Appointment(clientUuid, therapistUuid, LocalDateTime.now().minusDays(2), LocalDateTime.now().minusDays(2).plusHours(1), "To Past", "CONFIRMED");
        Assertions.assertThrows(ResponseStatusException.class, () -> appointmentController.rescheduleAppointment(appId, toPastReq));

        // Reject rescheduling or cancelling an appointment that is already in the past
        UUID pastAppId = UUID.randomUUID();
        Appointment pastAppointment = new Appointment(clientUuid, therapistUuid, LocalDateTime.now().minusDays(3), LocalDateTime.now().minusDays(3).plusHours(1), "Past Session", "CONFIRMED");
        pastAppointment.setId(pastAppId);
        Mockito.when(appointmentRepository.findById(pastAppId)).thenReturn(Optional.of(pastAppointment));
        Assertions.assertThrows(ResponseStatusException.class, () -> appointmentController.rescheduleAppointment(pastAppId, updateReq));
        Assertions.assertThrows(ResponseStatusException.class, () -> appointmentController.cancelAppointment(pastAppId));

        // Reschedule with null title preserves existing title & ignores cancelled appointments
        Appointment cancelledOther = new Appointment(UUID.randomUUID(), therapistUuid, start.plusHours(6), start.plusHours(7), "Cancelled", "CANCELLED");
        cancelledOther.setId(UUID.randomUUID());
        Appointment beforeSlot = new Appointment(UUID.randomUUID(), therapistUuid, start.minusHours(3), start.minusHours(2), "Early", "CONFIRMED");
        beforeSlot.setId(UUID.randomUUID());
        Mockito.when(appointmentRepository.findByTherapistUuid(therapistUuid))
                .thenReturn(List.of(existing, otherBooked, cancelledOther, beforeSlot));

        Appointment nullTitleReq = new Appointment(clientUuid, therapistUuid, start.plusHours(6), start.plusHours(7), null, "CONFIRMED");
        Appointment rescheduledOverCancelled = appointmentController.rescheduleAppointment(appId, nullTitleReq).getBody();
        Assertions.assertEquals("Rescheduled Session", rescheduledOverCancelled.getTitle());

        // Create appointment over a cancelled slot and non-overlapping slots succeeds, overlapping confirmed fails, invalid range fails
        Appointment cancelledSlot2 = new Appointment(UUID.randomUUID(), therapistUuid, start.plusHours(8), start.plusHours(9), "Cancelled 2", "CANCELLED");
        cancelledSlot2.setId(UUID.randomUUID());
        Mockito.when(appointmentRepository.findByTherapistUuid(therapistUuid))
                .thenReturn(List.of(existing, otherBooked, cancelledOther, cancelledSlot2, beforeSlot));
        Appointment newOverCancelled = new Appointment(clientUuid, therapistUuid, start.plusHours(8), start.plusHours(9), "New Session", "PENDING");
        Assertions.assertEquals(200, appointmentController.createAppointment(newOverCancelled).getStatusCode().value());

        Appointment overlappingCreate = new Appointment(clientUuid, therapistUuid, start.plusHours(2).plusMinutes(10), start.plusHours(2).plusMinutes(40), "Overlap", "PENDING");
        Assertions.assertThrows(ResponseStatusException.class, () -> appointmentController.createAppointment(overlappingCreate));

        Appointment invalidCreate = new Appointment(clientUuid, therapistUuid, start.plusHours(5), start.plusHours(4), "Invalid", "PENDING");
        Assertions.assertThrows(ResponseStatusException.class, () -> appointmentController.createAppointment(invalidCreate));

        // Timefold Solver optimal slots endpoint verification
        String futureDateStr = start.toLocalDate().toString();
        var timefoldResp = appointmentController.getTimefoldOptimalSlots(therapistUuid, futureDateStr, 60, "10:00", null);
        Assertions.assertEquals(200, timefoldResp.getStatusCode().value());
        Assertions.assertNotNull(timefoldResp.getBody());
        Assertions.assertEquals("Timefold Solver 1.16.0", timefoldResp.getBody().get("solver"));
        Assertions.assertNotNull(timefoldResp.getBody().get("recommendedSlot"));
        Assertions.assertTrue((Boolean) timefoldResp.getBody().get("feasible"));

        // Cancel appointment & 404 cases
        appointmentController.cancelAppointment(appId);
        Assertions.assertEquals("CANCELLED", existing.getStatus());

        UUID missingId = UUID.randomUUID();
        Mockito.when(appointmentRepository.findById(missingId)).thenReturn(Optional.empty());
        Assertions.assertThrows(ResponseStatusException.class, () -> appointmentController.rescheduleAppointment(missingId, updateReq));
        Assertions.assertThrows(ResponseStatusException.class, () -> appointmentController.cancelAppointment(missingId));
    }

    @Test
    public void testAppointmentServiceRemainingBranchesAndConstructors() {
        UUID clientUuid = UUID.randomUUID();
        UUID therapistUuid = UUID.randomUUID();
        UUID aptId = UUID.randomUUID();
        LocalDateTime now = LocalDateTime.now().plusDays(2);

        // Cover 3-arg and 5-arg constructors
        PlatformCacheService cache = new PlatformCacheService();
        PlatformEventService events = new PlatformEventService();
        AppointmentService svc3 = new AppointmentService(appointmentRepository, cache, events);
        AppointmentService svc5 = new AppointmentService(appointmentRepository, cache, events, null, null);
        Assertions.assertNotNull(svc3);
        Assertions.assertNotNull(svc5);

        // Cover getAppointmentsForClient(UUID) & date range filtering (including null startTime, before range, after range, inside range)
        Appointment aNullStart = new Appointment(clientUuid, therapistUuid, null, null, "Null", "CONFIRMED");
        Appointment aBefore = new Appointment(clientUuid, therapistUuid, now.minusDays(10), now.minusDays(10).plusHours(1), "Before", "CONFIRMED");
        Appointment aInside = new Appointment(clientUuid, therapistUuid, now, now.plusHours(1), "Inside", "CONFIRMED");
        Appointment aAfter = new Appointment(clientUuid, therapistUuid, now.plusDays(10), now.plusDays(10).plusHours(1), "After", "CONFIRMED");
        Mockito.when(appointmentRepository.findByClientUuid(clientUuid)).thenReturn(List.of(aNullStart, aBefore, aInside, aAfter));

        Assertions.assertEquals(4, svc3.getAppointmentsForClient(clientUuid).size());
        Assertions.assertEquals(1, svc3.getAppointmentsForClient(clientUuid, now.toLocalDate().minusDays(1), now.toLocalDate().plusDays(1)).size());
        // Calling with only fromDate (toDate == null) or only toDate (fromDate == null)
        Assertions.assertEquals(4, svc3.getAppointmentsForClient(clientUuid, now.toLocalDate(), null).size());
        Assertions.assertEquals(4, svc3.getAppointmentsForClient(clientUuid, null, now.toLocalDate()).size());

        // Cover createAppointment when repository returns null, or saved has null clientUuid/id, or git throws exception
        Mockito.when(appointmentRepository.findByTherapistUuid(therapistUuid)).thenReturn(List.of());
        Mockito.when(appointmentRepository.save(any())).thenReturn(null);
        Assertions.assertNull(svc3.createAppointment(aInside));

        JournalGitService failingGit = Mockito.mock(JournalGitService.class);
        Mockito.when(failingGit.commitJournalEntryWithHash(any(), any(), any(), any())).thenThrow(new RuntimeException("Git err"));
        AppointmentService svcFailingGit = new AppointmentService(appointmentRepository, cache, events, null, null, failingGit, null);

        Appointment savedNullFields = new Appointment(null, therapistUuid, now, now.plusHours(1), "No Client", "CONFIRMED");
        Mockito.when(appointmentRepository.save(any())).thenReturn(savedNullFields);
        svcFailingGit.createAppointment(savedNullFields);

        Appointment savedWithClient = new Appointment(clientUuid, therapistUuid, now, now.plusHours(1), "With Client", "CONFIRMED");
        savedWithClient.setId(aptId);
        savedWithClient.setLocation("Room 1");
        Mockito.when(appointmentRepository.save(any())).thenReturn(savedWithClient);
        svcFailingGit.createAppointment(savedWithClient);

        // Cover rescheduleAppointment branches (null startTime on existing/updated, blank location, non-blank location, git failure, null clientUuid)
        Appointment existingNoClient = new Appointment(null, therapistUuid, null, now.plusHours(1), "Title", "CONFIRMED");
        existingNoClient.setId(aptId);
        Mockito.when(appointmentRepository.findById(aptId)).thenReturn(Optional.of(existingNoClient));
        Appointment updateWithLoc = new Appointment(null, therapistUuid, now.plusHours(2), now.plusHours(3), "New Title", "CONFIRMED");
        updateWithLoc.setLocation("New Room");
        svcFailingGit.rescheduleAppointment(aptId, updateWithLoc);

        existingNoClient.setClientUuid(clientUuid);
        existingNoClient.setStartTime(now.plusHours(1));
        updateWithLoc.setLocation("   ");
        svcFailingGit.rescheduleAppointment(aptId, updateWithLoc);

        // Cover cancelAppointment branches (null startTime, null clientUuid, git failure)
        existingNoClient.setStartTime(null);
        existingNoClient.setClientUuid(null);
        svcFailingGit.cancelAppointment(aptId);

        existingNoClient.setClientUuid(clientUuid);
        svcFailingGit.cancelAppointment(aptId);

        // Cover getAppointmentGitHistory branches (not found, null journalGitService, null clientUuid, filter branches)
        UUID missingAptId = UUID.randomUUID();
        Mockito.when(appointmentRepository.findById(missingAptId)).thenReturn(Optional.empty());
        Assertions.assertThrows(ResponseStatusException.class, () -> svc3.getAppointmentGitHistory(missingAptId));

        Assertions.assertTrue(svc3.getAppointmentGitHistory(aptId).isEmpty());
        existingNoClient.setClientUuid(null);
        Assertions.assertTrue(svcFailingGit.getAppointmentGitHistory(aptId).isEmpty());

        existingNoClient.setClientUuid(clientUuid);
        Mockito.when(failingGit.getCommitHistory(any(), Mockito.eq(false), any())).thenReturn(List.of(
                java.util.Map.of("message", "Contains " + aptId, "snapshot", ""),
                java.util.Map.of("message", "Other", "snapshot", "Refs-Appointment: #" + aptId),
                java.util.Map.of("message", "Other", "snapshot", "Raw " + aptId),
                java.util.Map.of("message", "Unrelated", "snapshot", "Unrelated")
        ));
        Assertions.assertEquals(3, svcFailingGit.getAppointmentGitHistory(aptId).size());
    }
}

