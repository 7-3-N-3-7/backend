package com.platform.controller;

import com.platform.entity.Appointment;
import com.platform.service.AppointmentService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/appointments")
@CrossOrigin(origins = "*")
public class AppointmentController {

    private final AppointmentService appointmentService;

    public AppointmentController(AppointmentService appointmentService) {
        this.appointmentService = appointmentService;
    }

    @GetMapping("/client/{clientUuid}")
    public ResponseEntity<List<Appointment>> getAppointmentsForClient(@PathVariable UUID clientUuid) {
        return ResponseEntity.ok(appointmentService.getAppointmentsForClient(clientUuid));
    }

    @GetMapping("/therapist/{therapistUuid}")
    public ResponseEntity<List<Appointment>> getAppointmentsForTherapist(@PathVariable UUID therapistUuid) {
        return ResponseEntity.ok(appointmentService.getAppointmentsForTherapist(therapistUuid));
    }

    @PostMapping
    public ResponseEntity<Appointment> createAppointment(@RequestBody Appointment appointment) {
        return ResponseEntity.ok(appointmentService.createAppointment(appointment));
    }

    @PutMapping("/{id}")
    public ResponseEntity<Appointment> rescheduleAppointment(@PathVariable UUID id, @RequestBody Appointment updated) {
        return ResponseEntity.ok(appointmentService.rescheduleAppointment(id, updated));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> cancelAppointment(@PathVariable UUID id) {
        appointmentService.cancelAppointment(id);
        return ResponseEntity.ok().build();
    }
}
