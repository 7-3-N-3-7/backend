package com.integrate.controller;

import com.integrate.entity.Appointment;
import com.integrate.repository.AppointmentRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/appointments")
@CrossOrigin(origins = "*")
public class AppointmentController {

    private final AppointmentRepository appointmentRepository;

    public AppointmentController(AppointmentRepository appointmentRepository) {
        this.appointmentRepository = appointmentRepository;
    }

    @GetMapping("/client/{clientUuid}")
    public ResponseEntity<List<Appointment>> getAppointmentsForClient(@PathVariable UUID clientUuid) {
        return ResponseEntity.ok(appointmentRepository.findByClientUuid(clientUuid));
    }

    @PostMapping
    public ResponseEntity<Appointment> createAppointment(@RequestBody Appointment appointment) {
        return ResponseEntity.ok(appointmentRepository.save(appointment));
    }
}
