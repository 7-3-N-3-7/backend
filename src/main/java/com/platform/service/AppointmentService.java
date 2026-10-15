package com.platform.service;

import com.platform.entity.Appointment;
import com.platform.entity.ProviderProfile;
import com.platform.repository.AppointmentRepository;
import com.platform.repository.ProviderProfileRepository;
import com.platform.solver.TimefoldBookingService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class AppointmentService {

    private final AppointmentRepository appointmentRepository;
    private final PlatformCacheService cacheService;
    private final PlatformEventService eventService;
    private final TimefoldBookingService timefoldBookingService;
    private final ProviderProfileRepository providerProfileRepository;

    public AppointmentService(AppointmentRepository appointmentRepository) {
        this(appointmentRepository, new PlatformCacheService(), new PlatformEventService(), new TimefoldBookingService(), null);
    }

    public AppointmentService(
            AppointmentRepository appointmentRepository,
            PlatformCacheService cacheService,
            PlatformEventService eventService
    ) {
        this(appointmentRepository, cacheService, eventService, new TimefoldBookingService(), null);
    }

    @Autowired
    public AppointmentService(
            AppointmentRepository appointmentRepository,
            PlatformCacheService cacheService,
            PlatformEventService eventService,
            TimefoldBookingService timefoldBookingService,
            ProviderProfileRepository providerProfileRepository
    ) {
        this.appointmentRepository = appointmentRepository;
        this.cacheService = Objects.requireNonNullElseGet(cacheService, PlatformCacheService::new);
        this.eventService = Objects.requireNonNullElseGet(eventService, PlatformEventService::new);
        this.timefoldBookingService = Objects.requireNonNullElseGet(timefoldBookingService, TimefoldBookingService::new);
        this.providerProfileRepository = providerProfileRepository;
    }

    public List<Appointment> getAppointmentsForClient(UUID clientUuid) {
        return getAppointmentsForClient(clientUuid, null, null);
    }

    public List<Appointment> getAppointmentsForClient(UUID clientUuid, LocalDate fromDate, LocalDate toDate) {
        String cacheKey = buildClientCacheKey(clientUuid, fromDate, toDate);
        List<Appointment> cached = cacheService.get(cacheKey);
        if (cached != null) {
            return cached;
        }

        List<Appointment> appointments = appointmentRepository.findByClientUuid(clientUuid);
        if (fromDate != null && toDate != null) {
            LocalDateTime startBoundary = fromDate.atStartOfDay();
            LocalDateTime endBoundary = toDate.plusDays(1).atStartOfDay();
            appointments = appointments.stream()
                    .filter(a -> a.getStartTime() != null
                            && !a.getStartTime().isBefore(startBoundary)
                            && a.getStartTime().isBefore(endBoundary))
                    .collect(Collectors.toList());
        }

        cacheService.put(cacheKey, appointments);
        return appointments;
    }

    public List<Appointment> getAppointmentsForTherapist(UUID therapistUuid) {
        return appointmentRepository.findByTherapistUuid(therapistUuid);
    }

    public Map<String, Object> getTimefoldSlotSuggestions(
            UUID therapistUuid,
            LocalDate date,
            int durationMinutes,
            LocalTime preferredTime,
            UUID excludeAppointmentId
    ) {
        List<Appointment> existingAppointments = appointmentRepository.findByTherapistUuid(therapistUuid);
        ProviderProfile profile = providerProfileRepository != null
                ? providerProfileRepository.findByUserUuid(therapistUuid).orElse(null)
                : null;
        return timefoldBookingService.suggestOptimalSlots(
                therapistUuid,
                date,
                durationMinutes,
                preferredTime,
                existingAppointments,
                profile,
                excludeAppointmentId
        );
    }

    public Appointment createAppointment(Appointment appointment) {
        if (appointment.getEndTime().isBefore(appointment.getStartTime())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid time range: End time must be after start time");
        }

        List<Appointment> existingAppointments = appointmentRepository.findByTherapistUuid(appointment.getTherapistUuid());
        for (Appointment existing : existingAppointments) {
            if ("CANCELLED".equalsIgnoreCase(existing.getStatus())) {
                continue;
            }
            if (appointment.getStartTime().isBefore(existing.getEndTime()) && appointment.getEndTime().isAfter(existing.getStartTime())) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Therapist is already booked during the requested slot");
            }
        }

        appointment.setStatus("CONFIRMED");
        Appointment saved = appointmentRepository.save(appointment);

        if (saved != null) {
            if (saved.getClientUuid() != null) {
                cacheService.evictByPrefix("appointments:client:" + saved.getClientUuid());
            }
            if (saved.getId() != null) {
                cacheService.put("appointment:" + saved.getId(), saved);
            }
            eventService.publishEvent(
                    "appointment-events",
                    "CREATED",
                    saved.getId() != null ? saved.getId().toString() : "",
                    Map.of(
                            "status", "CONFIRMED",
                            "clientUuid", String.valueOf(saved.getClientUuid()),
                            "therapistUuid", String.valueOf(saved.getTherapistUuid())
                    )
            );
        }
        return saved;
    }

    public Appointment rescheduleAppointment(UUID id, Appointment updated) {
        Appointment existing = appointmentRepository.findById(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Appointment not found"));

        LocalDateTime now = LocalDateTime.now();
        if (existing.getStartTime() != null && existing.getStartTime().isBefore(now)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cannot reschedule an appointment that is in the past");
        }
        if (updated.getStartTime() != null && updated.getStartTime().isBefore(now)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cannot reschedule an appointment to a time in the past");
        }

        if (updated.getEndTime().isBefore(updated.getStartTime())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid time range: End time must be after start time");
        }

        List<Appointment> therapistAppointments = appointmentRepository.findByTherapistUuid(existing.getTherapistUuid());
        for (Appointment other : therapistAppointments) {
            if (other.getId().equals(id) || "CANCELLED".equalsIgnoreCase(other.getStatus())) {
                continue;
            }
            if (updated.getStartTime().isBefore(other.getEndTime()) && updated.getEndTime().isAfter(other.getStartTime())) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Therapist is already booked during the requested slot");
            }
        }

        existing.setStartTime(updated.getStartTime());
        existing.setEndTime(updated.getEndTime());
        if (updated.getTitle() != null) {
            existing.setTitle(updated.getTitle());
        }
        existing.setStatus("CONFIRMED");
        Appointment saved = appointmentRepository.save(existing);
        if (existing.getClientUuid() != null) {
            cacheService.evictByPrefix("appointments:client:" + existing.getClientUuid());
        }
        cacheService.put("appointment:" + id, saved);
        return saved;
    }

    public void cancelAppointment(UUID id) {
        Appointment appointment = appointmentRepository.findById(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Appointment not found"));

        if (appointment.getStartTime() != null && appointment.getStartTime().isBefore(LocalDateTime.now())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cannot cancel an appointment that is in the past");
        }

        appointment.setStatus("CANCELLED");
        appointmentRepository.save(appointment);
        cacheService.evict("appointment:" + id);
        if (appointment.getClientUuid() != null) {
            cacheService.evictByPrefix("appointments:client:" + appointment.getClientUuid());
        }
        eventService.publishEvent(
                "appointment-events",
                "CANCELLED",
                id.toString(),
                Map.of("status", "CANCELLED")
        );
    }

    public String buildClientCacheKey(UUID clientUuid, LocalDate fromDate, LocalDate toDate) {
        if (fromDate != null && toDate != null) {
            return "appointments:client:" + clientUuid + ":" + fromDate + ":" + toDate;
        }
        return "appointments:client:" + clientUuid + ":all";
    }
}

