package com.platform.features.appointment;

import ai.timefold.solver.core.api.score.buildin.hardsoft.HardSoftScore;
import com.platform.config.PlatformCacheService;
import com.platform.config.PlatformEventService;
import com.platform.core.BaseDomainCore.DomainException;
import com.platform.core.BaseDomainCore.ErrorCode;
import com.platform.features.appointment.AppointmentFeature.Appointment;
import com.platform.features.appointment.AppointmentFeature.AppointmentController;
import com.platform.features.appointment.AppointmentFeature.AppointmentDto;
import com.platform.features.appointment.AppointmentFeature.AppointmentRepository;
import com.platform.features.appointment.AppointmentFeature.AppointmentService;
import com.platform.features.appointment.AppointmentFeature.TimefoldBookingService;
import com.platform.features.clinic.ClinicPlatformFeature.ProviderProfile;
import com.platform.features.clinic.ClinicPlatformFeature.ProviderProfilePort;
import com.platform.features.clinic.ClinicPlatformFeature.Service;
import com.platform.features.clinic.ClinicPlatformFeature.ServiceLookupPort;
import com.platform.features.journal.JournalFeature.JournalGitService;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;

public class AppointmentServiceTest {

    private AppointmentRepository appointmentRepository;
    private JournalGitService journalGitService;
    private AppointmentService appointmentService;
    private AppointmentController appointmentController;
    private final TimefoldBookingService solverService = new TimefoldBookingService();

    @BeforeEach
    public void setUp() {
        appointmentRepository = Mockito.mock(AppointmentRepository.class);
        journalGitService = Mockito.mock(JournalGitService.class);
        appointmentService = new AppointmentService(
                appointmentRepository,
                new PlatformCacheService(),
                new PlatformEventService(),
                null,
                null,
                journalGitService
        );
        appointmentController = new AppointmentController(appointmentService);
    }

    @Test
    public void testCreateAppointmentAndMetadata() {
        UUID clientUuid = UUID.randomUUID();
        UUID therapistUuid = UUID.randomUUID();
        UUID serviceId = UUID.randomUUID();
        UUID journalEntryId = UUID.randomUUID();
        LocalDateTime initialStart = LocalDateTime.now().plusDays(1).withHour(10).withMinute(0).withSecond(0).withNano(0);
        Appointment appointment = new Appointment(clientUuid, therapistUuid, initialStart, initialStart.plusHours(1), "Initial Assessment", "PENDING");
        appointment.setId(UUID.randomUUID());
        appointment.setServiceId(serviceId);
        appointment.setTherapistName("Dr. Maja Holm");
        appointment.setOrganisationName("Nordic Mind");
        appointment.setLocation("København K");
        appointment.setJournalEntryId(journalEntryId);

        Mockito.when(appointmentRepository.findByTherapistUuid(therapistUuid)).thenReturn(List.of());
        Mockito.when(appointmentRepository.save(any(Appointment.class))).thenAnswer(inv -> {
            Appointment a = inv.getArgument(0);
            a.setId(appointment.getId());
            return a;
        });

        AppointmentDto saved = appointmentController.createAppointment(appointment).getBody();
        Assertions.assertNotNull(saved.getId());
        Assertions.assertEquals("CONFIRMED", saved.getStatus());
        Assertions.assertEquals(serviceId, saved.getServiceId());
        Assertions.assertEquals("Dr. Maja Holm", saved.getTherapistName());
        Assertions.assertEquals("Nordic Mind", saved.getOrganisationName());
        Assertions.assertEquals("København K", saved.getLocation());
        Assertions.assertEquals(journalEntryId, saved.getJournalEntryId());
        Assertions.assertEquals(clientUuid, saved.getClientUuid());
        Assertions.assertEquals(therapistUuid, saved.getTherapistUuid());
        Assertions.assertNotNull(saved.getStartTime());
        Assertions.assertNotNull(saved.getEndTime());
        Assertions.assertEquals("Initial Assessment", saved.getTitle());
        Assertions.assertNull(saved.getGitCommitHash());
        Assertions.assertNull(saved.getClientName());

        // Test default methods on AppointmentRepository
        Mockito.doCallRealMethod().when(appointmentRepository).findAppointmentById(any());
        Mockito.doCallRealMethod().when(appointmentRepository).saveAppointment(any());
        Mockito.doCallRealMethod().when(appointmentRepository).linkJournalEntry(any(), any());
        Mockito.when(appointmentRepository.findById(appointment.getId())).thenReturn(Optional.of(appointment));
        Assertions.assertTrue(appointmentRepository.findAppointmentById(appointment.getId()).isPresent());
        Assertions.assertNotNull(appointmentRepository.saveAppointment(appointment));
        UUID linkedJournalId = UUID.randomUUID();
        appointmentRepository.linkJournalEntry(appointment.getId(), linkedJournalId);
        Assertions.assertEquals(linkedJournalId, appointment.getJournalEntryId());
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
        AppointmentDto rescheduled = appointmentController.rescheduleAppointment(appId, updateReq).getBody();
        Assertions.assertEquals("Rescheduled Session", rescheduled.getTitle());

        // Conflict on reschedule
        Appointment conflictReq = new Appointment(clientUuid, therapistUuid, start.plusHours(2).plusMinutes(15), start.plusHours(3).plusMinutes(15), "Conflict", "CONFIRMED");
        Assertions.assertThrows(DomainException.class, () -> appointmentController.rescheduleAppointment(appId, conflictReq));

        // Invalid time range on reschedule
        Appointment invalidRange = new Appointment(clientUuid, therapistUuid, start.plusHours(5), start.plusHours(4), "Invalid", "CONFIRMED");
        Assertions.assertThrows(DomainException.class, () -> appointmentController.rescheduleAppointment(appId, invalidRange));

        // Reject rescheduling TO a time in the past
        Appointment toPastReq = new Appointment(clientUuid, therapistUuid, LocalDateTime.now().minusDays(2), LocalDateTime.now().minusDays(2).plusHours(1), "To Past", "CONFIRMED");
        Assertions.assertThrows(DomainException.class, () -> appointmentController.rescheduleAppointment(appId, toPastReq));

        // Reject rescheduling or cancelling an appointment that is already in the past
        UUID pastAppId = UUID.randomUUID();
        Appointment pastAppointment = new Appointment(clientUuid, therapistUuid, LocalDateTime.now().minusDays(3), LocalDateTime.now().minusDays(3).plusHours(1), "Past Session", "CONFIRMED");
        pastAppointment.setId(pastAppId);
        Mockito.when(appointmentRepository.findById(pastAppId)).thenReturn(Optional.of(pastAppointment));
        Assertions.assertThrows(DomainException.class, () -> appointmentController.rescheduleAppointment(pastAppId, updateReq));
        Assertions.assertThrows(DomainException.class, () -> appointmentController.cancelAppointment(pastAppId));

        // Reschedule with null title preserves existing title & ignores cancelled appointments
        Appointment cancelledOther = new Appointment(UUID.randomUUID(), therapistUuid, start.plusHours(6), start.plusHours(7), "Cancelled", "CANCELLED");
        cancelledOther.setId(UUID.randomUUID());
        Appointment beforeSlot = new Appointment(UUID.randomUUID(), therapistUuid, start.minusHours(3), start.minusHours(2), "Early", "CONFIRMED");
        beforeSlot.setId(UUID.randomUUID());
        Mockito.when(appointmentRepository.findByTherapistUuid(therapistUuid))
                .thenReturn(List.of(existing, otherBooked, cancelledOther, beforeSlot));

        Appointment nullTitleReq = new Appointment(clientUuid, therapistUuid, start.plusHours(6), start.plusHours(7), null, "CONFIRMED");
        AppointmentDto rescheduledOverCancelled = appointmentController.rescheduleAppointment(appId, nullTitleReq).getBody();
        Assertions.assertEquals("Rescheduled Session", rescheduledOverCancelled.getTitle());

        // Create appointment over a cancelled slot succeeds, overlapping confirmed fails, invalid range fails
        Appointment cancelledSlot2 = new Appointment(UUID.randomUUID(), therapistUuid, start.plusHours(8), start.plusHours(9), "Cancelled 2", "CANCELLED");
        cancelledSlot2.setId(UUID.randomUUID());
        Mockito.when(appointmentRepository.findByTherapistUuid(therapistUuid))
                .thenReturn(List.of(existing, otherBooked, cancelledOther, cancelledSlot2, beforeSlot));
        Appointment newOverCancelled = new Appointment(clientUuid, therapistUuid, start.plusHours(8), start.plusHours(9), "New Session", "PENDING");
        Assertions.assertEquals(200, appointmentController.createAppointment(newOverCancelled).getStatusCode().value());

        Appointment overlappingCreate = new Appointment(clientUuid, therapistUuid, start.plusHours(2).plusMinutes(10), start.plusHours(2).plusMinutes(40), "Overlap", "PENDING");
        Assertions.assertThrows(DomainException.class, () -> appointmentController.createAppointment(overlappingCreate));

        Appointment invalidCreate = new Appointment(clientUuid, therapistUuid, start.plusHours(5), start.plusHours(4), "Invalid", "PENDING");
        Assertions.assertThrows(DomainException.class, () -> appointmentController.createAppointment(invalidCreate));

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
        Assertions.assertThrows(DomainException.class, () -> appointmentController.rescheduleAppointment(missingId, updateReq));
        Assertions.assertThrows(DomainException.class, () -> appointmentController.cancelAppointment(missingId));
    }

    @Test
    public void testAppointmentGitVersioningAndRemainingBranches() {
        UUID clientUuid = UUID.randomUUID();
        UUID therapistUuid = UUID.randomUUID();
        UUID aptId = UUID.randomUUID();
        LocalDateTime now = LocalDateTime.now().plusDays(2).withHour(10).withMinute(0).withSecond(0).withNano(0);

        // 1. Git commit on create, reschedule, cancel, and git history filter
        Appointment appointment = new Appointment(
                clientUuid,
                therapistUuid,
                now.minusDays(1),
                now.minusDays(1).plusHours(1),
                "Traumeterapi Session 1",
                "PENDING"
        );
        appointment.setId(aptId);
        appointment.setTherapistName("Dr. Maja Holm");
        appointment.setOrganisationName("Nordic Clinic");

        Mockito.when(appointmentRepository.findByTherapistUuid(therapistUuid)).thenReturn(List.of());
        Mockito.when(appointmentRepository.save(any(Appointment.class))).thenAnswer(inv -> inv.getArgument(0));
        Mockito.when(journalGitService.commitJournalEntryWithHash(
                eq(clientUuid.toString()),
                anyString(),
                anyString(),
                eq("Dr. Maja Holm")
        )).thenReturn("a1b2c3d4");

        Appointment created = appointmentService.createAppointment(appointment);
        Assertions.assertEquals("a1b2c3d4", created.getGitCommitHash());

        Mockito.when(appointmentRepository.findById(aptId)).thenReturn(Optional.of(created));
        Mockito.when(journalGitService.commitJournalEntryWithHash(
                eq(clientUuid.toString()),
                anyString(),
                anyString(),
                eq("Dr. Maja Holm")
        )).thenReturn("resched2");

        Appointment updateReq = new Appointment(clientUuid, therapistUuid, now.plusDays(1), now.plusDays(1).plusHours(1), "Opfølgende Samtale (Flyttet)", "CONFIRMED");
        Appointment updated = appointmentService.rescheduleAppointment(aptId, updateReq);
        Assertions.assertEquals("resched2", updated.getGitCommitHash());

        created.setTherapistName("Bo Jensen");
        Mockito.when(journalGitService.commitJournalEntryWithHash(
                eq(clientUuid.toString()),
                anyString(),
                anyString(),
                eq("Bo Jensen")
        )).thenReturn("cancel44");
        appointmentService.cancelAppointment(aptId);
        Assertions.assertEquals("CANCELLED", created.getStatus());
        Assertions.assertEquals("cancel44", created.getGitCommitHash());

        Map<String, String> commit1 = Map.of("commitHash", "c1", "message", "Aftale (" + aptId + ")", "snapshot", "Refs-Appointment: #" + aptId);
        Map<String, String> commit2 = Map.of("commitHash", "c2", "message", "Unrelated", "snapshot", "Other");
        Map<String, String> commit3 = Map.of("commitHash", "c3", "message", "Note", "snapshot", "Raw " + aptId);
        Mockito.when(journalGitService.getCommitHistory(eq(clientUuid.toString()), eq(false), any()))
                .thenReturn(List.of(commit1, commit2, commit3));
        Assertions.assertEquals(2, appointmentService.getAppointmentGitHistory(aptId).size());

        // 2. Cover 1-arg, 3-arg, 5-arg constructors and ProviderProfilePort
        PlatformCacheService cache = new PlatformCacheService();
        PlatformEventService events = new PlatformEventService();
        ProviderProfilePort mockPpPort = Mockito.mock(ProviderProfilePort.class);
        ProviderProfile pp = new ProviderProfile();
        pp.setWorkingHoursJson("08:00-16:00");
        Mockito.when(mockPpPort.findByUserUuid(therapistUuid)).thenReturn(Optional.of(pp));

        AppointmentService svc1 = new AppointmentService(appointmentRepository);
        AppointmentService svc3 = new AppointmentService(appointmentRepository, cache, events);
        AppointmentService svc5 = new AppointmentService(appointmentRepository, cache, events, solverService, mockPpPort);
        Assertions.assertNotNull(svc1);
        Assertions.assertNotNull(svc3);
        Assertions.assertNotNull(svc5);
        Assertions.assertEquals(appointment.getId(), svc1.toDto(appointment).id());
        Assertions.assertEquals(appointment.getId(), svc1.toEntity(svc1.toDto(appointment)).getId());
        Assertions.assertNull(svc1.toDto(null));
        Assertions.assertNull(svc1.toEntity(null));

        svc5.getTimefoldSlotSuggestions(therapistUuid, LocalDate.now().plusDays(1), 60, LocalTime.of(10, 0), null);

        // 3. Date range filtering & edge cases
        Appointment aNullStart = new Appointment(clientUuid, therapistUuid, null, null, "Null", "CONFIRMED");
        Appointment aBefore = new Appointment(clientUuid, therapistUuid, now.minusDays(10), now.minusDays(10).plusHours(1), "Before", "CONFIRMED");
        Appointment aInside = new Appointment(clientUuid, therapistUuid, now, now.plusHours(1), "Inside", "CONFIRMED");
        Appointment aAfter = new Appointment(clientUuid, therapistUuid, now.plusDays(10), now.plusDays(10).plusHours(1), "After", "CONFIRMED");
        Mockito.when(appointmentRepository.findByClientUuid(clientUuid)).thenReturn(List.of(aNullStart, aBefore, aInside, aAfter));

        Assertions.assertEquals(4, svc3.getAppointmentsForClient(clientUuid).size());
        Assertions.assertEquals(1, svc3.getAppointmentsForClient(clientUuid, now.toLocalDate().minusDays(1), now.toLocalDate().plusDays(1)).size());
        Assertions.assertEquals(4, svc3.getAppointmentsForClient(clientUuid, now.toLocalDate(), null).size());
        Assertions.assertEquals(4, svc3.getAppointmentsForClient(clientUuid, null, now.toLocalDate()).size());

        // 4. Null save / null clientUuid / git exception branches
        Mockito.when(appointmentRepository.findByTherapistUuid(therapistUuid)).thenReturn(List.of());
        Mockito.when(appointmentRepository.save(any())).thenReturn(null);
        Assertions.assertNull(svc3.createAppointment(aInside));

        JournalGitService failingGit = Mockito.mock(JournalGitService.class);
        Mockito.when(failingGit.commitJournalEntryWithHash(any(), any(), any(), any())).thenThrow(new RuntimeException("Git err"));
        AppointmentService svcFailingGit = new AppointmentService(appointmentRepository, cache, events, null, null, failingGit);

        Appointment savedNullFields = new Appointment(null, therapistUuid, now, now.plusHours(1), "No Client", "CONFIRMED");
        Mockito.when(appointmentRepository.save(any())).thenReturn(savedNullFields);
        svcFailingGit.createAppointment(savedNullFields);

        Appointment savedWithClient = new Appointment(clientUuid, therapistUuid, now, now.plusHours(1), "With Client", "CONFIRMED");
        savedWithClient.setId(aptId);
        savedWithClient.setLocation("Room 1");
        Mockito.when(appointmentRepository.save(any())).thenReturn(savedWithClient);
        svcFailingGit.createAppointment(savedWithClient);

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

        existingNoClient.setStartTime(null);
        existingNoClient.setClientUuid(null);
        svcFailingGit.cancelAppointment(aptId);

        existingNoClient.setClientUuid(clientUuid);
        svcFailingGit.cancelAppointment(aptId);

        UUID missingAptId = UUID.randomUUID();
        Mockito.when(appointmentRepository.findById(missingAptId)).thenReturn(Optional.empty());
        Assertions.assertThrows(DomainException.class, () -> svc3.getAppointmentGitHistory(missingAptId));
        Assertions.assertTrue(svc3.getAppointmentGitHistory(aptId).isEmpty());
        existingNoClient.setClientUuid(null);
        Assertions.assertTrue(svcFailingGit.getAppointmentGitHistory(aptId).isEmpty());

        // 5. Controller create (201 vs 200), update, delete, git-history, timefold-slots
        Appointment scheduledReq = new Appointment(clientUuid, therapistUuid, now, now.plusHours(1), "Sched", "SCHEDULED");
        scheduledReq.setId(aptId);
        scheduledReq.setServiceId(UUID.randomUUID());
        Mockito.when(appointmentRepository.save(any(Appointment.class))).thenAnswer(inv -> inv.getArgument(0));
        Mockito.when(appointmentRepository.findById(aptId)).thenReturn(Optional.of(scheduledReq));
        Assertions.assertEquals(201, appointmentController.create(appointmentService.toDto(scheduledReq)).getStatusCode().value());

        Appointment normalReq = new Appointment(clientUuid, therapistUuid, now.plusHours(2), now.plusHours(3), "Norm", "PENDING");
        Assertions.assertEquals(200, appointmentController.create(appointmentService.toDto(normalReq)).getStatusCode().value());
        Assertions.assertEquals(200, appointmentController.update(aptId, appointmentService.toDto(updateWithLoc)).getStatusCode().value());
        Assertions.assertEquals(200, appointmentController.getAppointmentGitHistory(aptId).getStatusCode().value());
        Assertions.assertEquals(200, appointmentController.getAppointmentsForClient(clientUuid, "2026-01-01", "2026-12-31").getStatusCode().value());
        Assertions.assertEquals(200, appointmentController.getAppointmentsForClient(clientUuid, "  ", "").getStatusCode().value());
        Assertions.assertEquals(200, appointmentController.getTimefoldOptimalSlots(therapistUuid, "2026-10-20", 60, "10:00:00", null).getStatusCode().value());
        Assertions.assertEquals(200, appointmentController.getTimefoldOptimalSlots(therapistUuid, "2026-10-20", 60, "  ", aptId).getStatusCode().value());
        Assertions.assertEquals(200, appointmentController.delete(aptId).getStatusCode().value());

        // Direct BaseService overridden methods on AppointmentService + migrated Booking validation tests
        Assertions.assertNotNull(appointmentService.create(new AppointmentDto(clientUuid, therapistUuid, now.plusHours(4), now.plusHours(5), "DirectCreate", "PENDING")));
        Assertions.assertNotNull(appointmentService.update(aptId, appointmentService.toDto(updateWithLoc)));
        appointmentService.deleteById(aptId);

        // Migrated BookingServiceTest scenarios (clientName + startTime bookings)
        LocalDateTime bookTime = LocalDateTime.now().plusDays(7).withHour(10).withMinute(0).withSecond(0).withNano(0);
        AppointmentDto conflictBookingDto = new AppointmentDto(null, "Anders And", bookTime);
        Mockito.when(appointmentRepository.existsByStartTime(bookTime)).thenReturn(true);
        DomainException conflictEx = Assertions.assertThrows(DomainException.class, () -> appointmentService.create(conflictBookingDto));
        Assertions.assertEquals(ErrorCode.CONFLICT, conflictEx.getErrorCode());

        Mockito.when(appointmentRepository.existsByStartTime(bookTime)).thenReturn(false);
        AppointmentDto createdBooking = appointmentController.create(new AppointmentDto(null, "Andersine And", bookTime)).getBody();
        Assertions.assertNotNull(createdBooking);
        Assertions.assertEquals("Andersine And", createdBooking.clientName());
        Assertions.assertEquals(bookTime, createdBooking.startTime());

        Appointment bookingEntity = new Appointment(aptId, "Joakim von And", bookTime);
        Assertions.assertEquals("Joakim von And", bookingEntity.getClientName());
        Appointment bookingEntityNullStart = new Appointment(aptId, "Joakim von And", null);
        Assertions.assertNull(bookingEntityNullStart.getEndTime());
        AppointmentDto dtoNullStart = new AppointmentDto(aptId, "Joakim", null);
        Assertions.assertNull(dtoNullStart.endTime());
        AppointmentDto dto13Arg = new AppointmentDto(aptId, clientUuid, therapistUuid, null, "Dr. A", "Org", "Loc", bookTime, bookTime.plusHours(1), "T", "CONFIRMED", "h", null);
        Assertions.assertEquals("Dr. A", dto13Arg.therapistName());
        Appointment updatedClientName = new Appointment(aptId, "Rip, Rap og Rup", bookTime.plusHours(2));
        Mockito.when(appointmentRepository.findById(aptId)).thenReturn(Optional.of(bookingEntity));
        appointmentService.rescheduleAppointment(aptId, updatedClientName);
        Assertions.assertEquals("Rip, Rap og Rup", bookingEntity.getClientName());
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testTimefoldBookingServiceFullCoverage() {
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

        Appointment otherTherapistApt = new Appointment();
        otherTherapistApt.setId(UUID.randomUUID());
        otherTherapistApt.setTherapistUuid(UUID.randomUUID());
        otherTherapistApt.setStartTime(LocalDateTime.of(futureDate, LocalTime.of(10, 0)));
        otherTherapistApt.setEndTime(LocalDateTime.of(futureDate, LocalTime.of(11, 0)));
        otherTherapistApt.setStatus("SCHEDULED");

        ProviderProfile profile = new ProviderProfile();
        profile.setWorkingHoursJson("{\"monday\":\"09:00-15:00\"}");

        Map<String, Object> result = solverService.suggestOptimalSlots(
                therapistUuid,
                futureDate,
                60,
                LocalTime.of(11, 0),
                List.of(existing10to11, cancelled11to12, otherTherapistApt),
                profile,
                null
        );

        assertEquals("Timefold Solver 1.16.0", result.get("solver"));
        assertEquals("11:00", result.get("recommendedSlot"));
        assertEquals(Boolean.TRUE, result.get("feasible"));
        assertEquals("09:00-15:00", result.get("workingHoursWindow"));

        List<Map<String, Object>> slots = (List<Map<String, Object>>) result.get("slots");
        assertEquals(11, slots.size());

        // Past date & exclusions
        LocalDate pastDate = LocalDate.now().minusDays(5);
        UUID excludedId = UUID.randomUUID();
        Appointment excludedApt = new Appointment();
        excludedApt.setId(excludedId);
        excludedApt.setTherapistUuid(therapistUuid);
        excludedApt.setStartTime(LocalDateTime.of(pastDate, LocalTime.of(10, 0)));
        excludedApt.setEndTime(LocalDateTime.of(pastDate, LocalTime.of(11, 0)));
        excludedApt.setStatus("SCHEDULED");

        Map<String, Object> pastResult = solverService.suggestOptimalSlots(
                therapistUuid, pastDate, 0, null, List.of(excludedApt), null, excludedId
        );
        assertEquals(Boolean.FALSE, pastResult.get("feasible"));
        assertNull(pastResult.get("recommendedSlot"));

        // parseWorkingWindow branches
        assertEquals(LocalTime.of(8, 0), solverService.parseWorkingWindow(null)[0]);
        assertEquals(LocalTime.of(8, 0), solverService.parseWorkingWindow("   ")[0]);
        assertEquals(LocalTime.of(8, 0), solverService.parseWorkingWindow("16:00-09:00")[0]);
        assertEquals(LocalTime.of(8, 0), solverService.parseWorkingWindow("99:99-88:88")[0]);
        assertEquals(LocalTime.of(8, 0), solverService.parseWorkingWindow("no-hours")[0]);
        LocalTime[] validWindow = solverService.parseWorkingWindow("08:30 - 16:30");
        assertEquals(LocalTime.of(8, 30), validWindow[0]);
        assertEquals(LocalTime.of(16, 30), validWindow[1]);

        // Domain model constructors & getters
        TimefoldBookingService.CandidateSlot emptySlot = new TimefoldBookingService.CandidateSlot();
        assertNull(emptySlot.getId());
        TimefoldBookingService.ExistingBookingFact emptyFact = new TimefoldBookingService.ExistingBookingFact();
        assertNull(emptyFact.getId());
        TimefoldBookingService.ExistingBookingFact autoIdFact = new TimefoldBookingService.ExistingBookingFact(
                null, therapistUuid, LocalDateTime.now(), LocalDateTime.now().plusHours(1)
        );
        assertNotNull(autoIdFact.getId());
        assertEquals(therapistUuid, autoIdFact.getTherapistUuid());
        assertNotNull(autoIdFact.getStartTime());
        assertNotNull(autoIdFact.getEndTime());

        TimefoldBookingService.BookingAssignment emptyAssignment = new TimefoldBookingService.BookingAssignment();
        emptyAssignment.setAssignedSlot(null);
        assertNull(emptyAssignment.getAssignedSlot());
        assertEquals(0, emptyAssignment.getDurationMinutes());

        UUID assignmentId = UUID.randomUUID();
        TimefoldBookingService.BookingAssignment fullAssignment = new TimefoldBookingService.BookingAssignment(
                assignmentId, therapistUuid, 45, LocalDateTime.now(), emptySlot
        );
        assertEquals(assignmentId, fullAssignment.getId());
        assertEquals(therapistUuid, fullAssignment.getTherapistUuid());
        assertEquals(45, fullAssignment.getDurationMinutes());

        TimefoldBookingService.TherapistScheduleSolution emptySolution = new TimefoldBookingService.TherapistScheduleSolution();
        emptySolution.setScore(HardSoftScore.ZERO);
        assertEquals(HardSoftScore.ZERO, emptySolution.getScore());
        assertNull(emptySolution.getCandidateSlots());
        assertNull(emptySolution.getExistingBookings());
        assertNull(emptySolution.getAssignments());
        assertNotNull(emptySolution.getWorkingWindows());

        solverService.suggestOptimalSlots(therapistUuid, futureDate, 60, LocalTime.of(9, 0), null, null, null);
        Appointment nullStartApt = new Appointment(UUID.randomUUID(), therapistUuid, null, null, "Null Start", "CONFIRMED");
        Appointment nullEndApt = new Appointment(UUID.randomUUID(), therapistUuid, LocalDateTime.of(futureDate, LocalTime.of(9, 0)), null, "Null End", "CONFIRMED");
        Appointment diffDayApt = new Appointment(UUID.randomUUID(), therapistUuid, LocalDateTime.of(futureDate.plusDays(1), LocalTime.of(10, 0)), LocalDateTime.of(futureDate.plusDays(1), LocalTime.of(11, 0)), "Diff Day", "CONFIRMED");
        diffDayApt.setId(UUID.randomUUID());
        List<Appointment> mixedList = new ArrayList<>();
        mixedList.add(null);
        mixedList.add(nullStartApt);
        mixedList.add(nullEndApt);
        mixedList.add(diffDayApt);
        solverService.suggestOptimalSlots(therapistUuid, futureDate, 60, LocalTime.of(9, 0), mixedList, null, UUID.randomUUID());
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testMultiWindowSchedulesTimefoldRejectionAndServiceDuration() {
        UUID therapistUuid = UUID.randomUUID();
        UUID clientUuid = UUID.randomUUID();
        LocalDate nextMonday = LocalDate.now().plusWeeks(2).with(TemporalAdjusters.next(DayOfWeek.MONDAY));
        LocalDate nextSunday = nextMonday.plusDays(6);

        // 1. Version 1 structured JSON schedule (Monday two windows, Sunday closed, midnight end)
        String structuredJson = """
                {
                  "version": 1,
                  "days": [
                    {"day": "monday", "enabled": true, "intervals": [{"start": "08:30", "end": "12:00"}, {"start": "12:45", "end": "16:00"}]},
                    {"day": "tuesday", "enabled": true, "intervals": [{"start": "20:00", "end": "00:00"}]},
                    {"day": "wednesday", "enabled": true, "intervals": [{"start": "10:00", "end": "24:00"}]},
                    {"day": "thursday", "enabled": true, "intervals": [{"start": "bad", "end": "12:00"}]},
                    {"day": "sunday", "enabled": false, "intervals": []}
                  ]
                }
                """;
        ProviderProfile structuredProfile = new ProviderProfile();
        structuredProfile.setWorkingHoursJson(structuredJson);

        Map<String, Object> mondaySlotsResp = solverService.suggestOptimalSlots(
                therapistUuid, nextMonday, 45, LocalTime.of(12, 0), List.of(), structuredProfile, null, false
        );
        assertEquals(Boolean.TRUE, mondaySlotsResp.get("feasible"));
        List<Map<String, Object>> mondaySlots = (List<Map<String, Object>>) mondaySlotsResp.get("slots");
        Assertions.assertTrue(mondaySlots.stream().noneMatch(s -> "12:00".equals(s.get("slot"))));

        // Tuesday shift ending at 00:00 midnight & Wednesday ending 24:00
        Assertions.assertFalse(solverService.resolveWorkingWindowsForDate(structuredProfile, nextMonday.plusDays(1)).isEmpty());
        Assertions.assertFalse(solverService.resolveWorkingWindowsForDate(structuredProfile, nextMonday.plusDays(2)).isEmpty());
        // Thursday malformed interval & Friday missing from days[] & Sunday disabled -> closed
        Assertions.assertTrue(solverService.resolveWorkingWindowsForDate(structuredProfile, nextMonday.plusDays(3)).isEmpty());
        Assertions.assertTrue(solverService.resolveWorkingWindowsForDate(structuredProfile, nextMonday.plusDays(4)).isEmpty());

        Map<String, Object> sundayClosedResp = solverService.suggestOptimalSlots(
                therapistUuid, nextSunday, 60, LocalTime.of(10, 0), List.of(), structuredProfile, null, false
        );
        assertEquals(Boolean.FALSE, sundayClosedResp.get("feasible"));
        assertEquals(Boolean.TRUE, sundayClosedResp.get("closed"));
        assertEquals("Lukket", sundayClosedResp.get("workingHoursWindow"));

        // includeOutsideHours = true opens 07:00-21:00 even on Sunday
        Map<String, Object> sundayExtendedResp = solverService.suggestOptimalSlots(
                therapistUuid, nextSunday, 60, LocalTime.of(19, 0), List.of(), structuredProfile, null, true
        );
        assertEquals(Boolean.TRUE, sundayExtendedResp.get("feasible"));
        List<Map<String, Object>> extendedSlots = (List<Map<String, Object>>) sundayExtendedResp.get("slots");
        Assertions.assertFalse(extendedSlots.isEmpty());
        Assertions.assertTrue(extendedSlots.stream().anyMatch(s -> Boolean.FALSE.equals(s.get("withinWorkingHours"))));

        // Malformed JSON, non-array days, invalid interval bounds, blank/null profiles & plain multi-window text
        Assertions.assertEquals(1, solverService.resolveWorkingWindowsForDate(null, nextMonday).size());
        Assertions.assertEquals(1, solverService.resolveWorkingWindowsForDate(new ProviderProfile(), nextMonday).size());

        ProviderProfile brokenJsonProfile = new ProviderProfile();
        brokenJsonProfile.setWorkingHoursJson("{broken");
        assertEquals(1, solverService.resolveWorkingWindowsForDate(brokenJsonProfile, nextMonday).size());

        ProviderProfile nonArrayDaysProfile = new ProviderProfile();
        nonArrayDaysProfile.setWorkingHoursJson("{\"days\":\"invalid\"}");
        assertEquals(1, solverService.resolveWorkingWindowsForDate(nonArrayDaysProfile, nextMonday).size());

        ProviderProfile badBoundsProfile = new ProviderProfile();
        badBoundsProfile.setWorkingHoursJson("""
                {"days":[{"day":"monday","enabled":true,"intervals":[{"start":"24:00","end":"23:00"},{"start":"09:00","end":"bad"},{"start":"15:00","end":"13:00"}]}]}
                """);
        Assertions.assertTrue(solverService.resolveWorkingWindowsForDate(badBoundsProfile, nextMonday).isEmpty());

        ProviderProfile multiTextProfile = new ProviderProfile();
        multiTextProfile.setWorkingHoursJson("09:00-12:00, 10:00-13:00, 14:00-16:00, 17:00-15:00, 09:00-99:99, 99:00-12:00");
        assertEquals(3, solverService.resolveWorkingWindowsForDate(multiTextProfile, nextMonday).size());
        Appointment rightAfterOverlapCheck = new Appointment(
                UUID.randomUUID(), therapistUuid,
                LocalDateTime.of(nextMonday, LocalTime.of(10, 0)),
                LocalDateTime.of(nextMonday, LocalTime.of(11, 0)),
                "Adjacent", "CONFIRMED"
        );
        rightAfterOverlapCheck.setId(UUID.randomUUID());
        solverService.suggestOptimalSlots(
                therapistUuid, nextMonday, 60, LocalTime.of(9, 0),
                List.of(rightAfterOverlapCheck), multiTextProfile, null, false
        );

        // TimeWindowFact edge cases (null args, midnight crossing, out-of-range, inverted range)
        TimefoldBookingService.TimeWindowFact eveningWin = new TimefoldBookingService.TimeWindowFact("ev", 20 * 60, 1440);
        Assertions.assertFalse(eveningWin.contains(null, LocalDateTime.now()));
        Assertions.assertFalse(eveningWin.contains(LocalDateTime.now(), null));
        Assertions.assertTrue(eveningWin.contains(
                LocalDateTime.of(nextMonday, LocalTime.of(22, 0)),
                LocalDateTime.of(nextMonday.plusDays(1), LocalTime.MIDNIGHT)
        ));
        Assertions.assertFalse(eveningWin.contains(
                LocalDateTime.of(nextMonday, LocalTime.of(22, 0)),
                LocalDateTime.of(nextMonday.plusDays(2), LocalTime.MIDNIGHT)
        ));
        Assertions.assertFalse(eveningWin.contains(
                LocalDateTime.of(nextMonday, LocalTime.of(22, 0)),
                LocalDateTime.of(nextMonday.plusDays(1), LocalTime.of(1, 0))
        ));
        TimefoldBookingService.TimeWindowFact middayWin = new TimefoldBookingService.TimeWindowFact("mid", 9 * 60, 12 * 60);
        Assertions.assertFalse(middayWin.contains(
                LocalDateTime.of(nextMonday, LocalTime.of(8, 0)),
                LocalDateTime.of(nextMonday, LocalTime.of(9, 0))
        ));
        Assertions.assertFalse(middayWin.contains(
                LocalDateTime.of(nextMonday, LocalTime.of(11, 30)),
                LocalDateTime.of(nextMonday, LocalTime.of(12, 30))
        ));
        Assertions.assertFalse(middayWin.contains(
                LocalDateTime.of(nextMonday, LocalTime.of(10, 0)),
                LocalDateTime.of(nextMonday, LocalTime.of(10, 0))
        ));

        // 2. ServiceLookupPort endTime derivation + Self-booking rejection + Timefold JWT enforcement
        UUID serviceId = UUID.randomUUID();
        Service emdrService = new Service();
        emdrService.setId(serviceId);
        emdrService.setDurationMinutes(45);

        ProviderProfilePort ppPort = Mockito.mock(ProviderProfilePort.class);
        ServiceLookupPort svcPort = Mockito.mock(ServiceLookupPort.class);
        Mockito.when(ppPort.findByUserUuid(therapistUuid)).thenReturn(Optional.of(structuredProfile));
        Mockito.when(svcPort.findServiceById(serviceId)).thenReturn(Optional.of(emdrService));
        Mockito.when(appointmentRepository.findByTherapistUuid(therapistUuid)).thenReturn(List.of());
        Mockito.when(appointmentRepository.save(any(Appointment.class))).thenAnswer(i -> i.getArgument(0));

        AppointmentService fullSvc = new AppointmentService(
                appointmentRepository,
                new PlatformCacheService(),
                new PlatformEventService(),
                solverService,
                ppPort,
                journalGitService,
                svcPort
        );
        AppointmentController fullCtrl = new AppointmentController(fullSvc);

        // Self-booking rejected
        AppointmentDto selfDto = new AppointmentDto(
                null, therapistUuid, therapistUuid, serviceId, "Bo", "Bo", "Org", "Klinik",
                LocalDateTime.of(nextMonday, LocalTime.of(9, 0)), null, "Self", "SCHEDULED", null, null
        );
        Assertions.assertThrows(DomainException.class, () -> fullCtrl.create(selfDto));

        // Client JWT authenticated -> enforces working hours & derives 45m endTime
        try {
            Jwt clientJwt = Jwt.withTokenValue("cli-tok")
                    .header("alg", "none")
                    .claim("sub", clientUuid.toString())
                    .issuedAt(Instant.now())
                    .expiresAt(Instant.now().plusSeconds(3600))
                    .build();
            SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(clientJwt));

            // Lunch break (12:00 - 12:45) rejected as 409 CONFLICT by Timefold
            AppointmentDto breakBooking = new AppointmentDto(
                    null, clientUuid, therapistUuid, serviceId, "Anders", "Maja", "Org", "Klinik",
                    LocalDateTime.of(nextMonday, LocalTime.of(12, 0)), null, "Frokost", "SCHEDULED", null, null
            );
            DomainException breakEx = Assertions.assertThrows(DomainException.class, () -> fullCtrl.create(breakBooking));
            assertEquals(ErrorCode.CONFLICT, breakEx.getErrorCode());

            // Inside Monday morning window (09:00) derives 09:45 endTime and succeeds
            AppointmentDto validClientBooking = new AppointmentDto(
                    null, clientUuid, therapistUuid, serviceId, "Anders", "Maja", "Org", "Klinik",
                    LocalDateTime.of(nextMonday, LocalTime.of(9, 0)), null, "EMDR", "SCHEDULED", null, null
            );
            AppointmentDto created45 = fullCtrl.create(validClientBooking).getBody();
            assertEquals(LocalDateTime.of(nextMonday, LocalTime.of(9, 45)), created45.endTime());

            // Equal endTime == startTime rejected as BAD_REQUEST
            AppointmentDto equalTime = new AppointmentDto(
                    null, clientUuid, therapistUuid, serviceId, "Anders", "Maja", "Org", "Klinik",
                    LocalDateTime.of(nextMonday, LocalTime.of(10, 0)),
                    LocalDateTime.of(nextMonday, LocalTime.of(10, 0)),
                    "Bad Range", "SCHEDULED", null, null
            );
            Assertions.assertThrows(DomainException.class, () -> fullCtrl.create(equalTime));

            // Therapist JWT (sub == therapistUuid) allowed to book outside working hours (19:00)
            Jwt therapistJwt = Jwt.withTokenValue("th-tok")
                    .header("alg", "none")
                    .claim("sub", therapistUuid.toString())
                    .issuedAt(Instant.now())
                    .expiresAt(Instant.now().plusSeconds(3600))
                    .build();
            SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(therapistJwt));

            AppointmentDto eveningTherapistBooking = new AppointmentDto(
                    null, clientUuid, therapistUuid, serviceId, "Anders", "Maja", "Org", "Klinik",
                    LocalDateTime.of(nextMonday, LocalTime.of(19, 0)), null, "Aften", "SCHEDULED", null, null
            );
            AppointmentDto createdEvening = fullCtrl.create(eveningTherapistBooking).getBody();
            assertEquals(LocalDateTime.of(nextMonday, LocalTime.of(19, 45)), createdEvening.endTime());
        } finally {
            SecurityContextHolder.clearContext();
        }

        // Controller includeOutsideHours query param
        assertEquals(200, fullCtrl.getTimefoldOptimalSlots(therapistUuid, nextSunday.toString(), 60, "19:00", null, true).getStatusCode().value());

        // Direct assertBookingAllowed & AppointmentService defensive branch coverage
        solverService.assertBookingAllowed(null, null, null, null, null, null, true);
        solverService.assertBookingAllowed(therapistUuid, null, LocalDateTime.now(), null, null, null, true);
        solverService.assertBookingAllowed(therapistUuid, LocalDateTime.now(), null, null, null, null, true);

        ProviderProfile blankHoursProfile = new ProviderProfile();
        blankHoursProfile.setWorkingHoursJson("   ");
        solverService.assertBookingAllowed(
                therapistUuid,
                LocalDateTime.of(nextMonday, LocalTime.of(9, 0)),
                LocalDateTime.of(nextMonday, LocalTime.of(10, 0)),
                List.of(),
                new ProviderProfile(),
                null,
                true
        );
        solverService.assertBookingAllowed(
                therapistUuid,
                LocalDateTime.of(nextMonday, LocalTime.of(9, 0)),
                LocalDateTime.of(nextMonday, LocalTime.of(10, 0)),
                List.of(),
                blankHoursProfile,
                null,
                true
        );

        UUID zeroDurationServiceId = UUID.randomUUID();
        Service zeroDurSvc = new Service();
        zeroDurSvc.setId(zeroDurationServiceId);
        zeroDurSvc.setDurationMinutes(0);
        Mockito.when(svcPort.findServiceById(zeroDurationServiceId)).thenReturn(Optional.of(zeroDurSvc));

        try {
            // Non-JWT auth + JWT with null sub + missing therapist profile + fallback service duration
            SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken("user", "pass"));
            Mockito.when(ppPort.findByUserUuid(therapistUuid)).thenReturn(Optional.empty());

            AppointmentDto noServiceFallback = new AppointmentDto(
                    null, null, therapistUuid, null, "Anders", "Maja", "Org", "Klinik",
                    LocalDateTime.of(nextMonday, LocalTime.of(14, 0)), null, "Fallback60", "SCHEDULED", null, null
            );
            assertEquals(
                    LocalDateTime.of(nextMonday, LocalTime.of(15, 0)),
                    fullCtrl.create(noServiceFallback).getBody().endTime()
            );

            Jwt nullSubJwt = Jwt.withTokenValue("null-sub")
                    .header("alg", "none")
                    .claim("preferred_username", "x")
                    .issuedAt(Instant.now())
                    .expiresAt(Instant.now().plusSeconds(3600))
                    .build();
            SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(nullSubJwt));
            AppointmentDto zeroDurReq = new AppointmentDto(
                    null, clientUuid, null, zeroDurationServiceId, "WalkIn", null, null, null,
                    LocalDateTime.of(nextMonday, LocalTime.of(16, 0)), null, "ZeroDur", "SCHEDULED", null, null
            );
            assertEquals(
                    LocalDateTime.of(nextMonday, LocalTime.of(17, 0)),
                    fullCtrl.create(zeroDurReq).getBody().endTime()
            );

            // Null startTime on createAppointment + reschedule with missing provider profile & null startTime
            Appointment nullStartEntity = new Appointment(clientUuid, therapistUuid, null, null, "NoStart", "PENDING");
            Assertions.assertNotNull(fullSvc.createAppointment(nullStartEntity));

            UUID aptExistingId = UUID.randomUUID();
            Appointment existingScheduled = new Appointment(
                    clientUuid, therapistUuid,
                    LocalDateTime.of(nextMonday, LocalTime.of(10, 0)),
                    LocalDateTime.of(nextMonday, LocalTime.of(11, 0)),
                    "Existing", "CONFIRMED"
            );
            existingScheduled.setId(aptExistingId);
            Mockito.when(appointmentRepository.findById(aptExistingId)).thenReturn(Optional.of(existingScheduled));
            Appointment nullStartUpdate = new Appointment(clientUuid, therapistUuid, null, null, "UpdatedTitle", "CONFIRMED");
            Assertions.assertNotNull(fullSvc.rescheduleAppointment(aptExistingId, nullStartUpdate));
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testRemainingJaCoCoBranches() {
        UUID clientUuid = UUID.randomUUID();
        UUID therapistUuid = UUID.randomUUID();
        UUID otherTherapistUuid = UUID.randomUUID();
        UUID serviceId = UUID.randomUUID();
        LocalDateTime futureStart = LocalDateTime.now().plusDays(3).withHour(10).withMinute(0).withSecond(0).withNano(0);

        // 1. BookingConstraintProvider: preferredStartTime == null (L527) & different therapistUuid in ExistingBookingFact (L495)
        var solverFactory = (ai.timefold.solver.core.api.solver.SolverFactory<AppointmentFeature.TimefoldBookingService.TherapistScheduleSolution>)
                org.springframework.test.util.ReflectionTestUtils.getField(solverService, "solverFactory");
        var slot = new AppointmentFeature.TimefoldBookingService.CandidateSlot("10:00", futureStart, futureStart.plusHours(1), true, false);
        var otherFact = new AppointmentFeature.TimefoldBookingService.ExistingBookingFact(UUID.randomUUID(), otherTherapistUuid, futureStart, futureStart.plusHours(1));
        var winFact = new AppointmentFeature.TimefoldBookingService.TimeWindowFact("08:00-17:00", 8 * 60, 17 * 60);
        var nullPrefAssign = new AppointmentFeature.TimefoldBookingService.BookingAssignment(UUID.randomUUID(), therapistUuid, 60, null, slot);
        var problem = new AppointmentFeature.TimefoldBookingService.TherapistScheduleSolution(
                List.of(slot), List.of(otherFact), List.of(nullPrefAssign), List.of(winFact)
        );
        Assertions.assertNotNull(solverFactory.buildSolver().solve(problem));

        // 2. resolveWorkingWindowsForDate with blank whitespace workingHoursJson (L822)
        ProviderProfile whitespaceProfile = new ProviderProfile();
        whitespaceProfile.setWorkingHoursJson("   ");
        Assertions.assertFalse(solverService.resolveWorkingWindowsForDate(whitespaceProfile, LocalDate.now().plusDays(1)).isEmpty());

        // 3. AppointmentService with journalGitService == null and serviceLookupPort == null (L1097, L1117, L1157, L1244, L1305, L1347, L1393)
        AppointmentService bareSvc = new AppointmentService(
                appointmentRepository, new PlatformCacheService(), new PlatformEventService(), solverService, null
        );
        AppointmentController bareCtrl = new AppointmentController(bareSvc);
        Mockito.when(appointmentRepository.save(any(Appointment.class))).thenAnswer(i -> i.getArgument(0));
        Mockito.when(appointmentRepository.findByClientUuid(clientUuid)).thenReturn(List.of());

        // getAppointmentsForClient with fromDate != null && toDate == null (L1157)
        Assertions.assertTrue(bareSvc.getAppointmentsForClient(clientUuid, LocalDate.now(), null).isEmpty());

        // createAppointment: journalGitService == null with clientUuid != null (L1244)
        Appointment aptBare = new Appointment(clientUuid, null, null, null, "NoStartNoTherapist", "PENDING");
        aptBare.setServiceId(serviceId);
        Assertions.assertNotNull(bareSvc.createAppointment(aptBare));

        // createAppointment with non-null journalGitService & clientUuid == null (L1244) + validateBeforeCreate therapistUuid == null && startTime == null (L1117)
        AppointmentDto noClientStartDto = new AppointmentDto(null, null, null, serviceId, "   ", null, null, null, futureStart, null, "NoClient", "CONFIRMED", null, null);
        // Also hits AppointmentController.create L1546 with clientName != null && isBlank() -> status CONFIRMED & serviceId == null returns 200
        AppointmentDto ctrlBlankName = new AppointmentDto(null, null, null, null, "   ", null, null, null, null, null, "BlankClient", "CONFIRMED", null, null);
        Assertions.assertEquals(200, bareCtrl.create(ctrlBlankName).getStatusCode().value());

        Appointment nullClientApt = new Appointment(null, therapistUuid, futureStart, futureStart.plusHours(1), "NoClientGit", "PENDING");
        Mockito.when(appointmentRepository.findByTherapistUuid(therapistUuid)).thenReturn(List.of());
        Assertions.assertNotNull(appointmentService.createAppointment(nullClientApt));

        // rescheduleAppointment: existing.startTime != null && existing.endTime == null (L1305), journalGitService == null (L1347), clientUuid == null (L1347)
        UUID noEndId = UUID.randomUUID();
        Appointment existingNoEnd = new Appointment(clientUuid, null, futureStart, null, "NoEnd", "CONFIRMED");
        existingNoEnd.setId(noEndId);
        existingNoEnd.setServiceId(serviceId);
        Mockito.when(appointmentRepository.findById(noEndId)).thenReturn(Optional.of(existingNoEnd));
        Assertions.assertNotNull(bareSvc.rescheduleAppointment(noEndId, new Appointment(clientUuid, null, futureStart.plusHours(1), null, "Resch", "CONFIRMED")));

        UUID noClientAptId = UUID.randomUUID();
        Appointment existingNoClient = new Appointment(null, null, futureStart, futureStart.plusHours(1), "NoClient", "CONFIRMED");
        existingNoClient.setId(noClientAptId);
        Mockito.when(appointmentRepository.findById(noClientAptId)).thenReturn(Optional.of(existingNoClient));
        Assertions.assertNotNull(appointmentService.rescheduleAppointment(noClientAptId, new Appointment(null, null, futureStart.plusHours(2), null, "Resch2", "CONFIRMED")));

        // cancelAppointment: journalGitService == null (L1393) & clientUuid == null (L1393)
        bareSvc.cancelAppointment(noEndId);
        appointmentService.cancelAppointment(noClientAptId);
    }
}
