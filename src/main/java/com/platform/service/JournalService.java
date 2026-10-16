package com.platform.service;

<<<<<<< HEAD
<<<<<<< HEAD
<<<<<<< HEAD
=======
>>>>>>> 16f2191da8c19882528cd7f0f6ff79480a370715
import com.platform.core.service.BaseService;
import com.platform.crm.model.JournalEntry;
import com.platform.crm.model.TherapistClinicalNote;
import com.platform.crm.repository.JournalEntryRepository;
import com.platform.crm.repository.TherapistClinicalNoteRepository;
import com.platform.dto.JournalEntryDto;
import com.platform.entity.ClientTherapistHandshake;
import com.platform.repository.AppointmentRepository;
import com.platform.repository.HandshakeRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
public class JournalService extends BaseService<JournalEntry, JournalEntryDto, UUID> {

    private static final String INTERNAL_NOTES_MARKER = "<!-- INTERNAL_MEDICAL_NOTES -->";
    private static final Pattern INTERNAL_NOTES_PATTERN =
        Pattern.compile(
            INTERNAL_NOTES_MARKER + "[\\s\\S]*?(?=(\\r?\\n## |\\r?\\n" + INTERNAL_NOTES_MARKER + "|$))"
        );

    private JournalEntryRepository journalEntryRepository;
    private JournalGitService journalGitService;
    private TherapistClinicalNoteRepository clinicalNoteRepository;
    private PlatformCacheService cacheService;
    private AppointmentRepository appointmentRepository;
    private HandshakeRepository handshakeRepository;

    public JournalService() {
        super();
        this.cacheService = new PlatformCacheService();
    }

    @Autowired
    public JournalService(
            @Autowired(required = false) JournalEntryRepository journalEntryRepository,
            @Autowired(required = false) JournalGitService journalGitService,
            @Autowired(required = false) TherapistClinicalNoteRepository clinicalNoteRepository,
            @Autowired(required = false) PlatformCacheService cacheService,
            @Autowired(required = false) AppointmentRepository appointmentRepository,
            @Autowired(required = false) HandshakeRepository handshakeRepository
    ) {
        super(journalEntryRepository);
        this.journalEntryRepository = journalEntryRepository;
        this.journalGitService = journalGitService;
        this.clinicalNoteRepository = clinicalNoteRepository;
        this.cacheService = Objects.requireNonNullElseGet(cacheService, PlatformCacheService::new);
        this.appointmentRepository = appointmentRepository;
        this.handshakeRepository = handshakeRepository;
    }

    public JournalService withCollaborators(
            JournalEntryRepository journalEntryRepository,
            JournalGitService journalGitService,
            TherapistClinicalNoteRepository clinicalNoteRepository,
            PlatformCacheService cacheService,
            AppointmentRepository appointmentRepository,
            HandshakeRepository handshakeRepository
    ) {
        if (journalEntryRepository != null) {
            this.repository = journalEntryRepository;
            this.journalEntryRepository = journalEntryRepository;
        }
        if (journalGitService != null) {
            this.journalGitService = journalGitService;
        }
        if (clinicalNoteRepository != null) {
            this.clinicalNoteRepository = clinicalNoteRepository;
        }
        if (cacheService != null) {
            this.cacheService = cacheService;
        }
        if (appointmentRepository != null) {
            this.appointmentRepository = appointmentRepository;
        }
        if (handshakeRepository != null) {
            this.handshakeRepository = handshakeRepository;
        }
        return this;
    }

    public String getJournalContent(String rawMarkdown, boolean isTherapist) {
        if (rawMarkdown == null) {
            return "";
        }
        if (isTherapist) {
            return rawMarkdown;
        }
        return INTERNAL_NOTES_PATTERN.matcher(rawMarkdown)
                .replaceAll("")
                .replaceAll("\\n{3,}", "\n\n")
                .trim();
    }

    @Override
    public JournalEntry toEntity(JournalEntryDto dto) {
        if (dto == null) {
            return null;
        }
        UUID clientUuid = Objects.requireNonNullElse(
                dto.getClientUuid(),
                UUID.fromString("00000000-0000-0000-0000-000000000000")
        );
        String title = Objects.requireNonNullElse(dto.getTitle(), "Journalindlæg");
        JournalEntry entry = new JournalEntry(
                title,
                dto.getContent(),
                Objects.requireNonNullElse(dto.getMoodRating(), 7),
                LocalDateTime.now(),
                clientUuid
        );
        entry.setId(dto.getId());
        entry.setSharedWithTherapists(dto.getSharedWithTherapists());
        if (dto.getSharedTherapistUuids() != null) {
            entry.setSharedTherapistUuids(dto.getSharedTherapistUuids());
        }
        if (dto.getAppointmentId() != null) {
            entry.setAppointmentId(dto.getAppointmentId());
        }
        return entry;
    }

    public JournalEntry createJournalEntry(JournalEntryDto dto, Authentication authentication) {
        UUID clientUuid = dto.getClientUuid();
        String authorName = "Client";
        if (authentication != null && authentication.getPrincipal() instanceof Jwt jwt) {
            if (clientUuid == null) {
                try {
                    clientUuid = UUID.fromString(jwt.getSubject());
                } catch (IllegalArgumentException e) {
                    clientUuid = UUID.fromString("00000000-0000-0000-0000-000000000000");
                }
            }
            authorName = Objects.requireNonNullElse(jwt.getClaimAsString("preferred_username"), "Client");
        }
        if (clientUuid == null) {
            clientUuid = UUID.fromString("00000000-0000-0000-0000-000000000000");
        }
        dto.setClientUuid(clientUuid);

        JournalEntry entry = toEntity(dto);
        String title = entry.getTitle();

        if (journalGitService != null) {
            try {
                String commitMsg = "Added journal entry: " + title;
                String patientId = clientUuid.toString();
                String sharedInfo = Boolean.TRUE.equals(entry.getSharedWithTherapists())
                        ? (entry.getSharedTherapistUuids().isEmpty()
                                ? "All Active Handshake Therapists"
                                : entry.getSharedTherapistUuids().toString())
                        : "Private";
                String aptTrailer = (dto.getAppointmentId() != null && (dto.getContent() == null || !dto.getContent().contains("Refs-Appointment")))
                        ? "\nRefs-Appointment: #" + dto.getAppointmentId()
                        : "";
                String content = "## " + title + " (" + LocalDateTime.now() + ")\n"
                        + "Mood: " + entry.getMoodRating() + "/10 | Shared: " + sharedInfo + "\n"
                        + dto.getContent() + aptTrailer;
                String hash = journalGitService.commitJournalEntryWithHash(patientId, content, commitMsg, authorName);
                entry.setGitCommitHash(hash);
            } catch (Exception e) {
                System.err.println("Failed to commit to Git: " + e.getMessage());
            }
        }

        JournalEntry saved = journalEntryRepository.save(entry);
        if (saved != null && saved.getAppointmentId() != null && appointmentRepository != null) {
            appointmentRepository.findById(saved.getAppointmentId()).ifPresent(apt -> {
                apt.setJournalEntryId(saved.getId());
                appointmentRepository.save(apt);
            });
        }
        cacheService.put("journal:client:" + clientUuid, saved);
        return saved;
    }

    public JournalEntry updateJournalEntrySharing(UUID entryId, Map<String, Object> payload) {
        JournalEntry entry = journalEntryRepository.findById(entryId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Journal entry not found"));

        if (payload.containsKey("sharedWithTherapists") && payload.get("sharedWithTherapists") != null) {
            entry.setSharedWithTherapists(Boolean.parseBoolean(String.valueOf(payload.get("sharedWithTherapists"))));
        }
        if (payload.containsKey("sharedTherapistUuids")) {
            Object rawList = payload.get("sharedTherapistUuids");
            if (rawList instanceof List<?> list) {
                List<UUID> parsed = new ArrayList<>();
                for (Object item : list) {
                    if (item != null && !String.valueOf(item).isBlank()) {
                        try {
                            parsed.add(UUID.fromString(String.valueOf(item).trim()));
                        } catch (IllegalArgumentException ignored) {
                        }
                    }
                }
                entry.setSharedTherapistUuids(parsed);
            } else {
                entry.setSharedTherapistUuids(List.of());
            }
        }

        JournalEntry saved = journalEntryRepository.save(entry);
        if (saved.getClientUuid() != null) {
            cacheService.put("journal:client:" + saved.getClientUuid(), saved);
        }
        return saved;
    }

    public TherapistClinicalNote createTherapistNote(Map<String, Object> payload, Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof Jwt jwt)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Unauthorized");
        }

        if (!hasTherapistRole(authentication)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Access Denied: Required role ROLE_THERAPIST");
        }

        String clientUuidStr = Objects.toString(payload.get("clientUuid"), "");
        String noteText = Objects.toString(payload.getOrDefault("therapistNote", payload.get("content")), "");
        String therapistName = Objects.toString(payload.get("therapistName"), "Clinical Therapist");
        String organisationName = Objects.toString(payload.get("organisationName"), "Associated Clinic");
        UUID appointmentId = null;
        if (payload.get("appointmentId") != null && !String.valueOf(payload.get("appointmentId")).isBlank()) {
            try {
                appointmentId = UUID.fromString(String.valueOf(payload.get("appointmentId")).trim());
            } catch (IllegalArgumentException ignored) {
            }
        }

        if (!clientUuidStr.isBlank() && !noteText.isBlank()) {
            try {
                UUID clientUuid = UUID.fromString(clientUuidStr);
                UUID therapistUuid = parseUuidOrDefault(jwt.getSubject());

                String aptTrailer = (appointmentId != null && !noteText.contains("Refs-Appointment"))
                        ? "\nRefs-Appointment: #" + appointmentId
                        : "";
                String formattedInternalBlock = "<!-- INTERNAL_MEDICAL_NOTES -->\n"
                        + "**[CONFIDENTIAL CLINICAL NOTE | " + therapistName + " (" + organisationName + ") | " + LocalDateTime.now() + "]**\n"
                        + noteText + aptTrailer;
                String commitHash = journalGitService != null
                        ? journalGitService.commitJournalEntryWithHash(
                                clientUuid.toString(),
                                formattedInternalBlock,
                                "Clinical note by " + therapistName + " (" + organisationName + ")",
                                therapistName
                        )
                        : null;

                if (clinicalNoteRepository != null) {
                    TherapistClinicalNote clinicalNote = new TherapistClinicalNote(
                            clientUuid,
                            therapistUuid,
                            therapistName,
                            organisationName,
                            null,
                            appointmentId,
                            noteText,
                            commitHash
                    );
                    TherapistClinicalNote savedNote = clinicalNoteRepository.save(clinicalNote);
                    if (appointmentId != null && appointmentRepository != null) {
                        appointmentRepository.findById(appointmentId).ifPresent(apt -> {
                            apt.setJournalEntryId(savedNote.getId());
                            appointmentRepository.save(apt);
                        });
                    }
                    return savedNote;
                }
            } catch (Exception ignored) {
            }
        }
        return null;
    }

    public Map<String, Object> getClientJournalBundle(UUID clientUuid, UUID therapistUuid, Authentication auth) {
        boolean isTherapist = hasTherapistRole(auth) || therapistUuid != null;
        UUID callerUuid = therapistUuid != null ? therapistUuid : extractCallerUuid(auth);

        java.time.LocalDate historyStartDate = null;
        java.time.LocalDate historyEndDate = null;

        if (isTherapist && callerUuid != null && !callerUuid.equals(clientUuid)) {
            if (handshakeRepository != null) {
                Optional<ClientTherapistHandshake> hsOpt = handshakeRepository
                        .findByClientUuidAndTherapistUuid(clientUuid, callerUuid);

                if (hsOpt.isEmpty() || !hsOpt.get().isCurrentlyValid()) {
                    return Map.of(
                            "clientUuid", clientUuid,
                            "entries", List.of(),
                            "clinicalNotes", List.of()
                    );
                }

                ClientTherapistHandshake hs = hsOpt.get();
                historyStartDate = hs.getHistoryStartDate();
                historyEndDate = hs.getHistoryEndDate();
            }
        }

        List<JournalEntry> entries = journalEntryRepository.findByClientUuidOrderByTimestampDesc(clientUuid);
        if (isTherapist && callerUuid != null && !clientUuid.equals(callerUuid)) {
            entries = entries.stream()
                    .filter(e -> e.isVisibleToTherapist(callerUuid))
                    .collect(Collectors.toList());
        } else if (isTherapist) {
            entries = entries.stream()
                    .filter(JournalEntry::getSharedWithTherapists)
                    .collect(Collectors.toList());
        }

        if (isTherapist && (historyStartDate != null || historyEndDate != null)) {
            final java.time.LocalDate fStart = historyStartDate;
            final java.time.LocalDate fEnd = historyEndDate;
            entries = entries.stream().filter(e -> {
                if (e.getTimestamp() == null) return true;
                java.time.LocalDate entryDate = e.getTimestamp().toLocalDate();
                if (fStart != null && entryDate.isBefore(fStart)) return false;
                if (fEnd != null && entryDate.isAfter(fEnd)) return false;
                return true;
            }).collect(Collectors.toList());
        }

        List<TherapistClinicalNote> clinicalNotes = (isTherapist && clinicalNoteRepository != null)
                ? clinicalNoteRepository.findByClientUuidOrderByCreatedAtDesc(clientUuid)
                : List.of();

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("clientUuid", clientUuid);
        result.put("entries", entries);
        result.put("clinicalNotes", clinicalNotes);
        if (historyStartDate != null || historyEndDate != null) {
            result.put("historyFilterActive", true);
            result.put("historyStartDate", historyStartDate != null ? historyStartDate.toString() : null);
            result.put("historyEndDate", historyEndDate != null ? historyEndDate.toString() : null);
        }
        return result;
    }

    public List<Map<String, String>> getJournalCommitHistory(UUID clientUuid, UUID therapistUuid, Authentication auth) {
        boolean isTherapist = hasTherapistRole(auth) || therapistUuid != null;
        UUID callerUuid = therapistUuid != null ? therapistUuid : extractCallerUuid(auth);

        java.time.LocalDate historyStartDate = null;
        java.time.LocalDate historyEndDate = null;

        if (isTherapist && callerUuid != null && !callerUuid.equals(clientUuid)) {
            if (handshakeRepository != null) {
                Optional<ClientTherapistHandshake> hsOpt = handshakeRepository
                        .findByClientUuidAndTherapistUuid(clientUuid, callerUuid);

                if (hsOpt.isEmpty() || !hsOpt.get().isCurrentlyValid()) {
                    return List.of();
                }

                ClientTherapistHandshake hs = hsOpt.get();
                historyStartDate = hs.getHistoryStartDate();
                historyEndDate = hs.getHistoryEndDate();
            }
        }

        return journalGitService.getCommitHistory(
                clientUuid.toString(),
                isTherapist,
                this,
                historyStartDate,
                historyEndDate,
                callerUuid != null ? callerUuid.toString() : null
        );
    }

    public List<Map<String, String>> getClientTimeline(UUID clientUuid, Authentication auth) throws Exception {
        boolean isTherapist = hasTherapistRole(auth);
        String rawMarkdown = journalGitService.readJournalEntry(clientUuid.toString());
        String content = getJournalContent(rawMarkdown, isTherapist);
        return List.of(Map.of("id", clientUuid.toString(), "content", content));
    }

    public boolean hasTherapistRole(Authentication auth) {
        if (auth == null) {
            return false;
        }
        if (auth.getAuthorities() != null && auth.getAuthorities().stream()
                .anyMatch(a -> "ROLE_therapist".equalsIgnoreCase(a.getAuthority())
                        || "ROLE_ROLE_THERAPIST".equalsIgnoreCase(a.getAuthority()))) {
            return true;
        }
        if (auth.getPrincipal() instanceof Jwt jwt) {
            Map<String, Object> realmAccess = jwt.getClaimAsMap("realm_access");
            if (realmAccess != null && realmAccess.get("roles") instanceof List<?> roles) {
                return roles.stream().anyMatch(r ->
                        "therapist".equalsIgnoreCase(String.valueOf(r))
                                || "ROLE_THERAPIST".equalsIgnoreCase(String.valueOf(r)));
            }
        }
        return false;
    }

    public UUID extractCallerUuid(Authentication auth) {
        if (auth != null && auth.getPrincipal() instanceof Jwt jwt && jwt.getSubject() != null) {
            try {
                return UUID.fromString(jwt.getSubject());
            } catch (IllegalArgumentException ignored) {
            }
        }
        return null;
    }

    public UUID parseUuidOrDefault(String sub) {
        try {
            return UUID.fromString(sub);
        } catch (Exception ex) {
            return UUID.fromString("6ba7b810-9dad-11d1-80b4-00c04fd430c8");
        }
=======
<<<<<<< HEAD
=======
>>>>>>> 266b19092e0a4e40a20f5cd2eaf04be642298e92
=======
>>>>>>> 16f2191da8c19882528cd7f0f6ff79480a370715
import org.springframework.stereotype.Service;
import java.util.regex.Pattern;

@Service
public class JournalService {

    private static final String INTERNAL_NOTES_MARKER = "<!-- INTERNAL_MEDICAL_NOTES -->";
    private static final Pattern INTERNAL_NOTES_PATTERN = 
        Pattern.compile(INTERNAL_NOTES_MARKER + ".*", Pattern.DOTALL);

    public String getJournalContent(String rawMarkdown, boolean isTherapist) {
        if (isTherapist) {
            return rawMarkdown;
        }
        return INTERNAL_NOTES_PATTERN.matcher(rawMarkdown).replaceAll("").trim();
<<<<<<< HEAD
<<<<<<< HEAD
>>>>>>> 3d3aa53f18dac79b5509f233401d317d3db0787d
=======
>>>>>>> 266b19092e0a4e40a20f5cd2eaf04be642298e92
=======
>>>>>>> 3d3aa53f18dac79b5509f233401d317d3db0787d
>>>>>>> 16f2191da8c19882528cd7f0f6ff79480a370715
    }
}
