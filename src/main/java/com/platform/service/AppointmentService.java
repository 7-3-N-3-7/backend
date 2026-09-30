package com.platform.service;

import com.platform.entity.Appointment;
import com.platform.repository.AppointmentRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

@Service
public class AppointmentService {

    private final AppointmentRepository appointmentRepository;

    public AppointmentService(AppointmentRepository appointmentRepository) {
        this.appointmentRepository = appointmentRepository;
    }

    public List<Appointment> getAppointmentsForClient(UUID clientUuid) {
        return appointmentRepository.findByClientUuid(clientUuid);
    }

    public Appointment createAppointment(Appointment appointment) {
        if (appointment.getEndTime().isBefore(appointment.getStartTime())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid time range: End time must be after start time");
        }

        List<Appointment> existingAppointments = appointmentRepository.findByTherapistUuid(appointment.getTherapistUuid());
        for (Appointment existing : existingAppointments) {
            if (appointment.getStartTime().isBefore(existing.getEndTime()) && appointment.getEndTime().isAfter(existing.getStartTime())) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Therapist is already booked during the requested slot");
            }
        }

        appointment.setStatus("CONFIRMED");
        return appointmentRepository.save(appointment);
    }

    public void cancelAppointment(UUID id) {
        Appointment appointment = appointmentRepository.findById(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Appointment not found"));
        appointment.setStatus("CANCELLED");
        appointmentRepository.save(appointment);
    }
}
