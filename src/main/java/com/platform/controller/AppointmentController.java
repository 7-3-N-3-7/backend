package com.platform.controller;

import com.platform.core.controller.BaseController;
import com.platform.entity.Appointment;
<<<<<<< HEAD
<<<<<<< HEAD
<<<<<<< HEAD
import com.platform.service.AppointmentService;
=======
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
>>>>>>> fa4972dbc8a433d3937f53daa44e44c3ed6b905a
=======
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
>>>>>>> 3d3aa53f18dac79b5509f233401d317d3db0787d
=======
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
>>>>>>> 266b19092e0a4e40a20f5cd2eaf04be642298e92
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/appointments")
@CrossOrigin(origins = "*")
public class AppointmentController extends BaseController<Appointment, Appointment, UUID> {

<<<<<<< HEAD
<<<<<<< HEAD
    private final AppointmentService appointmentService;

    public AppointmentController(AppointmentService appointmentService) {
        super(appointmentService);
        this.appointmentService = appointmentService;
    }

    public ResponseEntity<List<Appointment>> getAppointmentsForClient(UUID clientUuid) {
        return getAppointmentsForClient(clientUuid, null, null);
    }

    @GetMapping("/client/{clientUuid}")
    public ResponseEntity<List<Appointment>> getAppointmentsForClient(
            @PathVariable UUID clientUuid,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to
    ) {
        java.time.LocalDate fromDate = (from != null && !from.isBlank()) ? java.time.LocalDate.parse(from) : null;
        java.time.LocalDate toDate = (to != null && !to.isBlank()) ? java.time.LocalDate.parse(to) : null;
        return ResponseEntity.ok(appointmentService.getAppointmentsForClient(clientUuid, fromDate, toDate));
=======
    private final com.platform.service.AppointmentService appointmentService;

=======
    private final com.platform.service.AppointmentService appointmentService;

>>>>>>> 266b19092e0a4e40a20f5cd2eaf04be642298e92
    public AppointmentController(com.platform.service.AppointmentService appointmentService) {
        this.appointmentService = appointmentService;
    }

    @GetMapping("/client/{clientUuid}")
    public ResponseEntity<List<Appointment>> getAppointmentsForClient(@PathVariable UUID clientUuid) {
        return ResponseEntity.ok(appointmentService.getAppointmentsForClient(clientUuid));
<<<<<<< HEAD
>>>>>>> 3d3aa53f18dac79b5509f233401d317d3db0787d
=======
>>>>>>> 266b19092e0a4e40a20f5cd2eaf04be642298e92
    }

    @GetMapping("/therapist/{therapistUuid}")
    public ResponseEntity<List<Appointment>> getAppointmentsForTherapist(@PathVariable UUID therapistUuid) {
        return ResponseEntity.ok(appointmentService.getAppointmentsForTherapist(therapistUuid));
    }

    @GetMapping("/timefold-slots")
    public ResponseEntity<java.util.Map<String, Object>> getTimefoldOptimalSlots(
            @RequestParam UUID therapistUuid,
            @RequestParam String date,
            @RequestParam(required = false, defaultValue = "60") int durationMinutes,
            @RequestParam(required = false) String preferredTime,
            @RequestParam(required = false) UUID excludeAppointmentId
    ) {
        java.time.LocalDate parsedDate = java.time.LocalDate.parse(date);
        java.time.LocalTime parsedPreferred = (preferredTime != null && !preferredTime.isBlank())
                ? java.time.LocalTime.parse(preferredTime.length() == 5 ? preferredTime : preferredTime.substring(0, 5))
                : java.time.LocalTime.of(10, 0);
        return ResponseEntity.ok(appointmentService.getTimefoldSlotSuggestions(
                therapistUuid,
                parsedDate,
                durationMinutes,
                parsedPreferred,
                excludeAppointmentId
        ));
    }

    @Override
    @PostMapping
<<<<<<< HEAD
    public ResponseEntity<Appointment> create(@RequestBody Appointment appointment) {
        return createAppointment(appointment);
    }

    public ResponseEntity<Appointment> createAppointment(Appointment appointment) {
        boolean requestedScheduled = "SCHEDULED".equalsIgnoreCase(appointment.getStatus()) || appointment.getServiceId() != null;
        Appointment created = appointmentService.createAppointment(appointment);
        if (requestedScheduled) {
            return ResponseEntity.status(org.springframework.http.HttpStatus.CREATED).body(created);
        }
        return ResponseEntity.ok(created);
    }

    @Override
    @PutMapping("/{id}")
    public ResponseEntity<Appointment> update(@PathVariable UUID id, @RequestBody Appointment updated) {
        return rescheduleAppointment(id, updated);
    }

    public ResponseEntity<Appointment> rescheduleAppointment(UUID id, Appointment updated) {
        return ResponseEntity.ok(appointmentService.rescheduleAppointment(id, updated));
    }

    @Override
    @DeleteMapping("/{id}")
<<<<<<< HEAD
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        return cancelAppointment(id);
    }

    public ResponseEntity<Void> cancelAppointment(UUID id) {
        appointmentService.cancelAppointment(id);
=======
=======
    public ResponseEntity<Appointment> createAppointment(@RequestBody Appointment appointment) {
        return ResponseEntity.ok(appointmentService.createAppointment(appointment));
    }

    @DeleteMapping("/{id}")
<<<<<<< HEAD
>>>>>>> 3d3aa53f18dac79b5509f233401d317d3db0787d
=======
>>>>>>> 266b19092e0a4e40a20f5cd2eaf04be642298e92
    public ResponseEntity<Void> cancelAppointment(@PathVariable UUID id, Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof Jwt jwt)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Unauthorized");
        }
        UUID callerUuid;
        try {
            callerUuid = UUID.fromString(jwt.getSubject());
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid user identity");
        }
        appointmentService.cancelAppointment(id, callerUuid);
<<<<<<< HEAD
<<<<<<< HEAD
>>>>>>> fa4972dbc8a433d3937f53daa44e44c3ed6b905a
        return ResponseEntity.ok().build();
    }

    @GetMapping("/{id}/git-history")
    public ResponseEntity<List<Map<String, String>>> getAppointmentGitHistory(@PathVariable UUID id) {
        return ResponseEntity.ok(appointmentService.getAppointmentGitHistory(id));
=======
        return ResponseEntity.ok().build();
>>>>>>> 3d3aa53f18dac79b5509f233401d317d3db0787d
=======
        return ResponseEntity.ok().build();
>>>>>>> 266b19092e0a4e40a20f5cd2eaf04be642298e92
    }
}
