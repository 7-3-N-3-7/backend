package com.platform.service;

import com.platform.controller.AppointmentController;
import com.platform.entity.Appointment;
import com.platform.repository.AppointmentRepository;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;

public class AppointmentGitVersioningTest {

    private AppointmentRepository appointmentRepository;
    private JournalGitService journalGitService;
    private AppointmentService appointmentService;
    private AppointmentController appointmentController;

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
                journalGitService,
                new JournalService()
        );
        appointmentController = new AppointmentController(appointmentService);
    }

    @Test
    public void testAppointmentCreationGeneratesGitCommit() {
        UUID clientUuid = UUID.randomUUID();
        UUID therapistUuid = UUID.randomUUID();
        Appointment appointment = new Appointment(
                clientUuid,
                therapistUuid,
                LocalDateTime.now().plusDays(1),
                LocalDateTime.now().plusDays(1).plusHours(1),
                "Traumeterapi Session 1",
                "PENDING"
        );
        appointment.setId(UUID.randomUUID());
        appointment.setTherapistName("Dr. Maja Holm");
        appointment.setOrganisationName("Nordic Clinic");

        Mockito.when(appointmentRepository.findByTherapistUuid(therapistUuid)).thenReturn(List.of());
        Mockito.when(appointmentRepository.save(any(Appointment.class))).thenAnswer(invocation -> invocation.getArgument(0));
        Mockito.when(journalGitService.commitJournalEntryWithHash(
                eq(clientUuid.toString()),
                anyString(),
                anyString(),
                eq("Dr. Maja Holm")
        )).thenReturn("a1b2c3d4");

        Appointment created = appointmentService.createAppointment(appointment);

        Assertions.assertNotNull(created);
        Assertions.assertEquals("a1b2c3d4", created.getGitCommitHash());
        Mockito.verify(journalGitService, Mockito.times(1)).commitJournalEntryWithHash(
                eq(clientUuid.toString()),
                Mockito.contains("Refs-Appointment: #" + appointment.getId()),
                Mockito.contains("Aftale oprettet: Traumeterapi Session 1"),
                eq("Dr. Maja Holm")
        );
    }

    @Test
    public void testAppointmentReschedulingGeneratesGitCommit() {
        UUID aptId = UUID.randomUUID();
        UUID clientUuid = UUID.randomUUID();
        UUID therapistUuid = UUID.randomUUID();
        LocalDateTime originalStart = LocalDateTime.now().plusDays(2);
        LocalDateTime originalEnd = originalStart.plusHours(1);

        Appointment existing = new Appointment(
                clientUuid,
                therapistUuid,
                originalStart,
                originalEnd,
                "Opfølgende Samtale",
                "CONFIRMED"
        );
        existing.setId(aptId);
        existing.setGitCommitHash("init1111");
        existing.setTherapistName("Dr. Maja Holm");

        Mockito.when(appointmentRepository.findById(aptId)).thenReturn(Optional.of(existing));
        Mockito.when(appointmentRepository.findByTherapistUuid(therapistUuid)).thenReturn(List.of(existing));
        Mockito.when(appointmentRepository.save(any(Appointment.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Mockito.when(journalGitService.commitJournalEntryWithHash(
                eq(clientUuid.toString()),
                anyString(),
                anyString(),
                eq("Dr. Maja Holm")
        )).thenReturn("resched2");

        Appointment updateReq = new Appointment();
        LocalDateTime newStart = LocalDateTime.now().plusDays(3);
        LocalDateTime newEnd = newStart.plusHours(1);
        updateReq.setStartTime(newStart);
        updateReq.setEndTime(newEnd);
        updateReq.setTitle("Opfølgende Samtale (Flyttet)");

        Appointment updated = appointmentService.rescheduleAppointment(aptId, updateReq);

        Assertions.assertEquals("resched2", updated.getGitCommitHash());
        Assertions.assertEquals("Opfølgende Samtale (Flyttet)", updated.getTitle());
        Mockito.verify(journalGitService, Mockito.times(1)).commitJournalEntryWithHash(
                eq(clientUuid.toString()),
                Mockito.contains("Refs-Commit: #init1111"),
                Mockito.contains("Aftale ombooket"),
                eq("Dr. Maja Holm")
        );
    }

    @Test
    public void testAppointmentCancellationGeneratesGitCommit() {
        UUID aptId = UUID.randomUUID();
        UUID clientUuid = UUID.randomUUID();
        UUID therapistUuid = UUID.randomUUID();

        Appointment existing = new Appointment(
                clientUuid,
                therapistUuid,
                LocalDateTime.now().plusDays(1),
                LocalDateTime.now().plusDays(1).plusHours(1),
                "Klinisk Vurdering",
                "CONFIRMED"
        );
        existing.setId(aptId);
        existing.setGitCommitHash("hash3333");
        existing.setTherapistName("Bo Jensen");

        Mockito.when(appointmentRepository.findById(aptId)).thenReturn(Optional.of(existing));
        Mockito.when(appointmentRepository.save(any(Appointment.class))).thenAnswer(invocation -> invocation.getArgument(0));
        Mockito.when(journalGitService.commitJournalEntryWithHash(
                eq(clientUuid.toString()),
                anyString(),
                anyString(),
                eq("Bo Jensen")
        )).thenReturn("cancel44");

        appointmentService.cancelAppointment(aptId);

        Assertions.assertEquals("CANCELLED", existing.getStatus());
        Assertions.assertEquals("cancel44", existing.getGitCommitHash());
        Mockito.verify(journalGitService, Mockito.times(1)).commitJournalEntryWithHash(
                eq(clientUuid.toString()),
                Mockito.contains("Refs-Appointment: #" + aptId),
                Mockito.contains("Aftale aflyst: Klinisk Vurdering"),
                eq("Bo Jensen")
        );
    }

    @Test
    public void testGetAppointmentGitHistoryFiltersByAppointmentId() {
        UUID aptId = UUID.randomUUID();
        UUID clientUuid = UUID.randomUUID();

        Appointment existing = new Appointment();
        existing.setId(aptId);
        existing.setClientUuid(clientUuid);

        Mockito.when(appointmentRepository.findById(aptId)).thenReturn(Optional.of(existing));

        Map<String, String> commit1 = Map.of(
                "commitHash", "c1",
                "message", "Aftale oprettet: Session (" + aptId + ")",
                "snapshot", "## Aftale\nRefs-Appointment: #" + aptId
        );
        Map<String, String> commit2 = Map.of(
                "commitHash", "c2",
                "message", "Unrelated journal entry",
                "snapshot", "Client felt better today"
        );
        Map<String, String> commit3 = Map.of(
                "commitHash", "c3",
                "message", "Clinical note by Bo Jensen",
                "snapshot", "Note details\nRefs-Appointment: #" + aptId
        );

        Mockito.when(journalGitService.getCommitHistory(eq(clientUuid.toString()), eq(false), any()))
                .thenReturn(List.of(commit1, commit2, commit3));

        List<Map<String, String>> history = appointmentService.getAppointmentGitHistory(aptId);

        Assertions.assertEquals(2, history.size());
        Assertions.assertEquals("c1", history.get(0).get("commitHash"));
        Assertions.assertEquals("c3", history.get(1).get("commitHash"));
    }

    @Test
    public void testAppointmentControllerAndServiceEdgeBranches() {
        UUID aptId = UUID.randomUUID();
        UUID clientUuid = UUID.randomUUID();
        UUID therapistUuid = UUID.randomUUID();

        // 1. AppointmentController create, update, delete, git-history, timefold-slots, client/therapist queries
        Appointment scheduledReq = new Appointment(
                clientUuid, therapistUuid,
                LocalDateTime.now().plusDays(1),
                LocalDateTime.now().plusDays(1).plusHours(1),
                "Scheduled Apt", "SCHEDULED"
        );
        scheduledReq.setId(aptId);
        scheduledReq.setLocation("Klinik A");
        Mockito.when(appointmentRepository.findByTherapistUuid(therapistUuid)).thenReturn(List.of());
        Mockito.when(appointmentRepository.save(any(Appointment.class))).thenAnswer(inv -> inv.getArgument(0));
        Mockito.when(appointmentRepository.findById(aptId)).thenReturn(Optional.of(scheduledReq));
        Mockito.when(appointmentRepository.findByClientUuid(clientUuid)).thenReturn(List.of(scheduledReq));

        Assertions.assertEquals(201, appointmentController.create(scheduledReq).getStatusCode().value());

        Appointment normalReq = new Appointment(
                clientUuid, therapistUuid,
                LocalDateTime.now().plusDays(2),
                LocalDateTime.now().plusDays(2).plusHours(1),
                "Normal Apt", "PENDING"
        );
        Assertions.assertEquals(200, appointmentController.create(normalReq).getStatusCode().value());

        Appointment updateReq = new Appointment(
                clientUuid, therapistUuid,
                LocalDateTime.now().plusDays(3),
                LocalDateTime.now().plusDays(3).plusHours(1),
                "Updated Apt", "CONFIRMED"
        );
        updateReq.setLocation("Online Video");
        Assertions.assertEquals(200, appointmentController.update(aptId, updateReq).getStatusCode().value());
        Assertions.assertEquals(200, appointmentController.getAppointmentGitHistory(aptId).getStatusCode().value());
        Assertions.assertEquals(200, appointmentController.getAppointmentsForClient(clientUuid).getStatusCode().value());
        Assertions.assertEquals(200, appointmentController.getAppointmentsForClient(clientUuid, "2026-01-01", "2026-12-31").getStatusCode().value());
        Assertions.assertEquals(200, appointmentController.getAppointmentsForClient(clientUuid, "  ", "").getStatusCode().value());
        Assertions.assertEquals(200, appointmentController.getAppointmentsForTherapist(therapistUuid).getStatusCode().value());
        Assertions.assertEquals(200, appointmentController.getTimefoldOptimalSlots(therapistUuid, "2026-10-20", 60, "10:00:00", null).getStatusCode().value());
        Assertions.assertEquals(200, appointmentController.getTimefoldOptimalSlots(therapistUuid, "2026-10-20", 60, "  ", aptId).getStatusCode().value());
        Assertions.assertEquals(200, appointmentController.delete(aptId).getStatusCode().value());

        // 2. Reschedule & Cancel error branches (past start time, end before start, overlap, git exception, null git service)
        Appointment pastApt = new Appointment(
                clientUuid, therapistUuid,
                LocalDateTime.now().minusDays(1),
                LocalDateTime.now().minusDays(1).plusHours(1),
                "Past", "CONFIRMED"
        );
        UUID pastId = UUID.randomUUID();
        pastApt.setId(pastId);
        Mockito.when(appointmentRepository.findById(pastId)).thenReturn(Optional.of(pastApt));
        Assertions.assertThrows(org.springframework.web.server.ResponseStatusException.class,
                () -> appointmentService.rescheduleAppointment(pastId, updateReq));
        Assertions.assertThrows(org.springframework.web.server.ResponseStatusException.class,
                () -> appointmentService.cancelAppointment(pastId));

        // Future existing, but updated target is in the past
        scheduledReq.setStartTime(LocalDateTime.now().plusDays(2));
        scheduledReq.setEndTime(LocalDateTime.now().plusDays(2).plusHours(1));
        scheduledReq.setStatus("CONFIRMED");
        Appointment targetInPast = new Appointment(
                clientUuid, therapistUuid,
                LocalDateTime.now().minusHours(2),
                LocalDateTime.now().minusHours(1),
                "Target Past", "CONFIRMED"
        );
        Assertions.assertThrows(org.springframework.web.server.ResponseStatusException.class,
                () -> appointmentService.rescheduleAppointment(aptId, targetInPast));

        // End before start in reschedule
        Appointment invalidRange = new Appointment(
                clientUuid, therapistUuid,
                LocalDateTime.now().plusDays(4).plusHours(2),
                LocalDateTime.now().plusDays(4),
                "Invalid Range", "CONFIRMED"
        );
        Assertions.assertThrows(org.springframework.web.server.ResponseStatusException.class,
                () -> appointmentService.rescheduleAppointment(aptId, invalidRange));

        // Overlap in reschedule with another active appointment
        Appointment otherActive = new Appointment(
                clientUuid, therapistUuid,
                LocalDateTime.now().plusDays(5),
                LocalDateTime.now().plusDays(5).plusHours(1),
                "Other Active", "CONFIRMED"
        );
        otherActive.setId(UUID.randomUUID());
        Appointment cancelledOther = new Appointment(
                clientUuid, therapistUuid,
                LocalDateTime.now().plusDays(5),
                LocalDateTime.now().plusDays(5).plusHours(1),
                "Cancelled Other", "CANCELLED"
        );
        cancelledOther.setId(UUID.randomUUID());
        Mockito.when(appointmentRepository.findByTherapistUuid(therapistUuid))
                .thenReturn(List.of(scheduledReq, cancelledOther, otherActive));

        Appointment conflictingUpdate = new Appointment(
                clientUuid, therapistUuid,
                otherActive.getStartTime().plusMinutes(15),
                otherActive.getEndTime().plusMinutes(15),
                "Conflict", "CONFIRMED"
        );
        Assertions.assertThrows(org.springframework.web.server.ResponseStatusException.class,
                () -> appointmentService.rescheduleAppointment(aptId, conflictingUpdate));

        // Git exception handling during create, reschedule, cancel
        Mockito.when(appointmentRepository.findByTherapistUuid(therapistUuid)).thenReturn(List.of(cancelledOther));
        Mockito.when(journalGitService.commitJournalEntryWithHash(anyString(), anyString(), anyString(), anyString()))
                .thenThrow(new RuntimeException("Simulated Git error"));
        appointmentService.create(scheduledReq);
        appointmentService.update(aptId, updateReq);
        appointmentService.deleteById(aptId);

        // AppointmentService without gitService
        AppointmentService noGitSvc = new AppointmentService(appointmentRepository);
        Assertions.assertTrue(noGitSvc.getAppointmentGitHistory(aptId).isEmpty());
    }
}
