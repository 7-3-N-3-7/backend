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

    @PostMapping
    public ResponseEntity<Appointment> createAppointment(@RequestBody Appointment appointment) {
        boolean requestedScheduled = "SCHEDULED".equalsIgnoreCase(appointment.getStatus()) || appointment.getServiceId() != null;
        Appointment created = appointmentService.createAppointment(appointment);
        if (requestedScheduled) {
            return ResponseEntity.status(org.springframework.http.HttpStatus.CREATED).body(created);
        }
        return ResponseEntity.ok(created);
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
