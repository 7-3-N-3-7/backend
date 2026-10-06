package com.platform.controller;

import com.platform.entity.Appointment;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/appointments")
@CrossOrigin(origins = "*")
public class AppointmentController {

    private final com.platform.service.AppointmentService appointmentService;

    public AppointmentController(com.platform.service.AppointmentService appointmentService) {
        this.appointmentService = appointmentService;
    }

    @GetMapping("/client/{clientUuid}")
    public ResponseEntity<List<Appointment>> getAppointmentsForClient(@PathVariable UUID clientUuid) {
        return ResponseEntity.ok(appointmentService.getAppointmentsForClient(clientUuid));
    }

    @PostMapping
    public ResponseEntity<Appointment> createAppointment(@RequestBody Appointment appointment) {
        return ResponseEntity.ok(appointmentService.createAppointment(appointment));
    }

    @DeleteMapping("/{id}")
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
        return ResponseEntity.ok().build();
    }
}
