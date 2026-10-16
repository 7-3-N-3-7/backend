package com.platform.service;

import com.platform.controller.AppointmentController;
import com.platform.entity.Appointment;
import com.platform.repository.AppointmentRepository;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
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
<<<<<<< HEAD
    private AppointmentController appointmentController;
=======
>>>>>>> fa4972dbc8a433d3937f53daa44e44c3ed6b905a

    @BeforeEach
    public void setUp() {
        appointmentRepository = Mockito.mock(AppointmentRepository.class);
        appointmentService = new AppointmentService(appointmentRepository);
<<<<<<< HEAD
        appointmentController = new AppointmentController(appointmentService);
=======
>>>>>>> fa4972dbc8a433d3937f53daa44e44c3ed6b905a
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
    public void testCreateAppointmentRejectsZeroLengthRange() {
        LocalDateTime start = LocalDateTime.now();
        Appointment appointment = new Appointment(UUID.randomUUID(), UUID.randomUUID(), start, start, "Initial Assessment", "PENDING");

        ResponseStatusException exception = Assertions.assertThrows(
                ResponseStatusException.class,
                () -> appointmentService.createAppointment(appointment));

        Assertions.assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
        Mockito.verify(appointmentRepository, Mockito.never()).save(any(Appointment.class));
    }

    @Test
    public void testCancelAppointmentRejectsUnrelatedCaller() {
        UUID clientUuid = UUID.randomUUID();
        UUID therapistUuid = UUID.randomUUID();
        Appointment appointment = new Appointment(clientUuid, therapistUuid,
                LocalDateTime.now(), LocalDateTime.now().plusHours(1), "Session", "CONFIRMED");
        UUID id = UUID.randomUUID();
        appointment.setId(id);
        Mockito.when(appointmentRepository.findById(id)).thenReturn(java.util.Optional.of(appointment));

        ResponseStatusException exception = Assertions.assertThrows(
                ResponseStatusException.class,
                () -> appointmentService.cancelAppointment(id, UUID.randomUUID()));

        Assertions.assertEquals(HttpStatus.FORBIDDEN, exception.getStatusCode());
        Mockito.verify(appointmentRepository, Mockito.never()).save(any(Appointment.class));
    }
}
