package com.platform.service;

import com.platform.core.service.BaseService;
import com.platform.entity.Appointment;
import com.platform.entity.ProviderProfile;
import com.platform.repository.AppointmentRepository;
import com.platform.repository.ProviderProfileRepository;
import com.platform.solver.TimefoldBookingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
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
public class AppointmentService extends BaseService<Appointment, Appointment, UUID> {

    private static final Logger log = LoggerFactory.getLogger(AppointmentService.class);

    private final AppointmentRepository appointmentRepository;
    private final PlatformCacheService cacheService;
    private final PlatformEventService eventService;
    private final TimefoldBookingService timefoldBookingService;
    private final ProviderProfileRepository providerProfileRepository;
    private final JournalGitService journalGitService;
    private final JournalService journalService;

    public AppointmentService(AppointmentRepository appointmentRepository) {
        this(appointmentRepository, new PlatformCacheService(), new PlatformEventService(), new TimefoldBookingService(), null, null, null);
    }

    public AppointmentService(
            AppointmentRepository appointmentRepository,
            PlatformCacheService cacheService,
            PlatformEventService eventService
    ) {
        this(appointmentRepository, cacheService, eventService, new TimefoldBookingService(), null, null, null);
    }

    public AppointmentService(
            AppointmentRepository appointmentRepository,
            PlatformCacheService cacheService,
            PlatformEventService eventService,
            TimefoldBookingService timefoldBookingService,
            ProviderProfileRepository providerProfileRepository
    ) {
        this(appointmentRepository, cacheService, eventService, timefoldBookingService, providerProfileRepository, null, null);
    }

    @Autowired
    public AppointmentService(
            AppointmentRepository appointmentRepository,
            PlatformCacheService cacheService,
            PlatformEventService eventService,
            TimefoldBookingService timefoldBookingService,
            ProviderProfileRepository providerProfileRepository,
            @Autowired(required = false) JournalGitService journalGitService,
            @Autowired(required = false) JournalService journalService
    ) {
        super(appointmentRepository);
        this.appointmentRepository = appointmentRepository;
        this.cacheService = Objects.requireNonNullElseGet(cacheService, PlatformCacheService::new);
        this.eventService = Objects.requireNonNullElseGet(eventService, PlatformEventService::new);
        this.timefoldBookingService = Objects.requireNonNullElseGet(timefoldBookingService, TimefoldBookingService::new);
        this.providerProfileRepository = providerProfileRepository;
        this.journalGitService = journalGitService;
        this.journalService = Objects.requireNonNullElseGet(journalService, JournalService::new);
    }

    @Override
    public Appointment create(Appointment entity) {
        return createAppointment(entity);
    }

    @Override
    public Appointment update(UUID id, Appointment entity) {
        return rescheduleAppointment(id, entity);
    }

    @Override
    public void deleteById(UUID id) {
        cancelAppointment(id);
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

    @Transactional(isolation = Isolation.SERIALIZABLE)
    public Appointment createAppointment(Appointment appointment) {
        if (!appointment.getEndTime().isAfter(appointment.getStartTime())) {
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
            if (journalGitService != null && saved.getClientUuid() != null) {
                try {
                    String title = Objects.requireNonNullElse(saved.getTitle(), "Aftale");
                    String author = Objects.requireNonNullElse(saved.getTherapistName(), "Klinik Booking");
                    String content = "## Aftale: " + title + "\n"
                            + "Tidspunkt: " + saved.getStartTime() + " - " + saved.getEndTime() + "\n"
                            + "Status: " + saved.getStatus() + "\n"
                            + "Terapeut: " + Objects.requireNonNullElse(saved.getTherapistName(), "-")
                            + " (" + Objects.requireNonNullElse(saved.getOrganisationName(), "-") + ")\n"
                            + (saved.getLocation() != null ? "Lokation: " + saved.getLocation() + "\n" : "")
                            + "Refs-Appointment: #" + saved.getId();
                    String commitMsg = "Aftale oprettet: " + title + " (" + saved.getStartTime() + ")";
                    String hash = journalGitService.commitJournalEntryWithHash(
                            saved.getClientUuid().toString(),
                            content,
                            commitMsg,
                            author
                    );
                    saved.setGitCommitHash(hash);
                    saved = appointmentRepository.save(saved);
                } catch (Exception e) {
                    log.warn("Failed to commit appointment to Git for client {}", saved.getClientUuid(), e);
                }
            }

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
                            "therapistUuid", String.valueOf(saved.getTherapistUuid()),
                            "gitCommitHash", String.valueOf(saved.getGitCommitHash())
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

        LocalDateTime previousStartTime = existing.getStartTime();
        String previousHash = existing.getGitCommitHash();

        existing.setStartTime(updated.getStartTime());
        existing.setEndTime(updated.getEndTime());
        if (updated.getTitle() != null) {
            existing.setTitle(updated.getTitle());
        }
        if (updated.getLocation() != null && !updated.getLocation().isBlank()) {
            existing.setLocation(updated.getLocation());
        }
        existing.setStatus("CONFIRMED");

        if (journalGitService != null && existing.getClientUuid() != null) {
            try {
                String title = Objects.requireNonNullElse(existing.getTitle(), "Aftale");
                String author = Objects.requireNonNullElse(existing.getTherapistName(), "Klinik Booking");
                String content = "## Aftale opdateret / ombooket: " + title + "\n"
                        + "Nyt tidspunkt: " + existing.getStartTime() + " - " + existing.getEndTime() + "\n"
                        + "Tidligere tidspunkt: " + previousStartTime + "\n"
                        + "Status: " + existing.getStatus() + "\n"
                        + "Terapeut: " + Objects.requireNonNullElse(existing.getTherapistName(), "-")
                        + " (" + Objects.requireNonNullElse(existing.getOrganisationName(), "-") + ")\n"
                        + (existing.getLocation() != null ? "Lokation: " + existing.getLocation() + "\n" : "")
                        + "Refs-Appointment: #" + existing.getId() + "\n"
                        + (previousHash != null ? "Refs-Commit: #" + previousHash : "");
                String commitMsg = "Aftale ombooket: " + title + " til " + existing.getStartTime();
                String hash = journalGitService.commitJournalEntryWithHash(
                        existing.getClientUuid().toString(),
                        content,
                        commitMsg,
                        author
                );
                existing.setGitCommitHash(hash);
            } catch (Exception e) {
                log.warn("Failed to commit appointment reschedule to Git for client {}", existing.getClientUuid(), e);
            }
        }

        Appointment saved = appointmentRepository.save(existing);
        if (existing.getClientUuid() != null) {
            cacheService.evictByPrefix("appointments:client:" + existing.getClientUuid());
        }
        cacheService.put("appointment:" + id, saved);
        return saved;
    }

    public void cancelAppointment(UUID id, UUID callerUuid) {
        Appointment appointment = appointmentRepository.findById(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Appointment not found"));
<<<<<<< HEAD

        if (appointment.getStartTime() != null && appointment.getStartTime().isBefore(LocalDateTime.now())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cannot cancel an appointment that is in the past");
        }

=======
        if (!callerUuid.equals(appointment.getClientUuid())
                && !callerUuid.equals(appointment.getTherapistUuid())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Forbidden");
        }
>>>>>>> fa4972dbc8a433d3937f53daa44e44c3ed6b905a
        appointment.setStatus("CANCELLED");

        if (journalGitService != null && appointment.getClientUuid() != null) {
            try {
                String title = Objects.requireNonNullElse(appointment.getTitle(), "Aftale");
                String author = Objects.requireNonNullElse(appointment.getTherapistName(), "Klinik Booking");
                String content = "## Aftale aflyst: " + title + "\n"
                        + "Tidligere tidspunkt: " + appointment.getStartTime() + " - " + appointment.getEndTime() + "\n"
                        + "Status: CANCELLED\n"
                        + "Terapeut: " + Objects.requireNonNullElse(appointment.getTherapistName(), "-")
                        + " (" + Objects.requireNonNullElse(appointment.getOrganisationName(), "-") + ")\n"
                        + (appointment.getLocation() != null ? "Lokation: " + appointment.getLocation() + "\n" : "")
                        + "Refs-Appointment: #" + appointment.getId() + "\n"
                        + (appointment.getGitCommitHash() != null ? "Refs-Commit: #" + appointment.getGitCommitHash() : "");
                String commitMsg = "Aftale aflyst: " + title;
                String hash = journalGitService.commitJournalEntryWithHash(
                        appointment.getClientUuid().toString(),
                        content,
                        commitMsg,
                        author
                );
                appointment.setGitCommitHash(hash);
            } catch (Exception e) {
                log.warn("Failed to commit appointment cancellation to Git for client {}", appointment.getClientUuid(), e);
            }
        }

        appointmentRepository.save(appointment);
        cacheService.evict("appointment:" + id);
        if (appointment.getClientUuid() != null) {
            cacheService.evictByPrefix("appointments:client:" + appointment.getClientUuid());
        }
        eventService.publishEvent(
                "appointment-events",
                "CANCELLED",
                id.toString(),
                Map.of(
                        "status", "CANCELLED",
                        "gitCommitHash", String.valueOf(appointment.getGitCommitHash())
                )
        );
    }

    public List<Map<String, String>> getAppointmentGitHistory(UUID appointmentId) {
        Appointment apt = appointmentRepository.findById(appointmentId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Appointment not found"));
        if (journalGitService == null || apt.getClientUuid() == null) {
            return List.of();
        }
        String refTag = "Refs-Appointment: #" + appointmentId;
        String aptIdStr = appointmentId.toString();
        List<Map<String, String>> clientHistory = journalGitService.getCommitHistory(
                apt.getClientUuid().toString(),
                false,
                this.journalService
        );
        return clientHistory.stream()
                .filter(commit -> {
                    String msg = commit.getOrDefault("message", "");
                    String snapshot = commit.getOrDefault("snapshot", "");
                    return msg.contains(aptIdStr) || snapshot.contains(refTag) || snapshot.contains(aptIdStr);
                })
                .collect(Collectors.toList());
    }

    public String buildClientCacheKey(UUID clientUuid, LocalDate fromDate, LocalDate toDate) {
        if (fromDate != null && toDate != null) {
            return "appointments:client:" + clientUuid + ":" + fromDate + ":" + toDate;
        }
        return "appointments:client:" + clientUuid + ":all";
    }
}

