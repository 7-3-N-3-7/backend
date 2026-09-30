package com.platform.controller;

import com.platform.entity.Appointment;
import com.platform.repository.AppointmentRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

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
    public ResponseEntity<Void> cancelAppointment(@PathVariable UUID id) {
        appointmentService.cancelAppointment(id);
        return ResponseEntity.ok().build();
    }
}
