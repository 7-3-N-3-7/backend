package com.platform.controller;

import com.platform.crm.model.JournalEntry;
import com.platform.crm.model.TherapistClinicalNote;
import com.platform.crm.repository.JournalEntryRepository;
import com.platform.crm.repository.TherapistClinicalNoteRepository;
import com.platform.dto.JournalEntryDto;
import com.platform.service.JournalGitService;
import com.platform.service.JournalService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/journals")
@CrossOrigin(origins = "*")
public class JournalController {

    private final JournalEntryRepository journalEntryRepository;
    private final JournalGitService journalGitService;
    private final TherapistClinicalNoteRepository clinicalNoteRepository;
    private final JournalService journalService;

    public JournalController(JournalEntryRepository journalEntryRepository, JournalGitService journalGitService) {
        this(journalEntryRepository, journalGitService, null, new JournalService());
    }

    @Autowired
    public JournalController(
            JournalEntryRepository journalEntryRepository,
            JournalGitService journalGitService,
            TherapistClinicalNoteRepository clinicalNoteRepository,
            JournalService journalService
    ) {
        this.journalEntryRepository = journalEntryRepository;
        this.journalGitService = journalGitService;
        this.clinicalNoteRepository = clinicalNoteRepository;
        this.journalService = Objects.requireNonNullElseGet(journalService, JournalService::new);
    }

    @PostMapping
    public ResponseEntity<JournalEntry> createJournalEntry(@RequestBody JournalEntryDto dto, Authentication authentication) {
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

        JournalEntry entry = new JournalEntry(
                dto.getTitle(),
                dto.getContent(),
                dto.getMoodRating(),
                LocalDateTime.now(),
                clientUuid
        );
        entry.setSharedWithTherapists(dto.getSharedWithTherapists());

        try {
            String commitMsg = "Added journal entry: " + dto.getTitle();
            String patientId = clientUuid.toString();
            String content = "## " + dto.getTitle() + " (" + LocalDateTime.now() + ")\n"
                    + "Mood: " + dto.getMoodRating() + "/10 | Shared: " + entry.getSharedWithTherapists() + "\n"
                    + dto.getContent();
            String hash = journalGitService.commitJournalEntryWithHash(patientId, content, commitMsg, authorName);
            entry.setGitCommitHash(hash);
        } catch (Exception e) {
            System.err.println("Failed to commit to Git: " + e.getMessage());
        }

        JournalEntry saved = journalEntryRepository.save(entry);
        return ResponseEntity.ok(saved);
    }

    @PostMapping("/therapist-notes")
    public ResponseEntity<?> createTherapistNote(@RequestBody Map<String, Object> payload, Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof Jwt jwt)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Unauthorized");
        }

        Map<String, Object> realmAccess = jwt.getClaimAsMap("realm_access");
        if (realmAccess == null || !((List<?>) realmAccess.get("roles")).contains("therapist")) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Access Denied: Required role ROLE_THERAPIST");
        }

        String clientUuidStr = Objects.toString(payload.get("clientUuid"), "");
        String noteText = Objects.toString(payload.getOrDefault("therapistNote", payload.get("content")), "");
        String therapistName = Objects.toString(payload.get("therapistName"), "Clinical Therapist");
        String organisationName = Objects.toString(payload.get("organisationName"), "Associated Clinic");

        if (!clientUuidStr.isBlank() && !noteText.isBlank()) {
            try {
                UUID clientUuid = UUID.fromString(clientUuidStr);
                UUID therapistUuid = parseUuidOrDefault(jwt.getSubject());

                String formattedInternalBlock = "<!-- INTERNAL_MEDICAL_NOTES -->\n"
                        + "**[CONFIDENTIAL CLINICAL NOTE | " + therapistName + " (" + organisationName + ") | " + LocalDateTime.now() + "]**\n"
                        + noteText;
                String commitHash = journalGitService.commitJournalEntryWithHash(
                        clientUuid.toString(),
                        formattedInternalBlock,
                        "Clinical note by " + therapistName + " (" + organisationName + ")",
                        therapistName
                );

                if (clinicalNoteRepository != null) {
                    TherapistClinicalNote clinicalNote = new TherapistClinicalNote(
                            clientUuid,
                            therapistUuid,
                            therapistName,
                            organisationName,
                            null,
                            noteText,
                            commitHash
                    );
                    return ResponseEntity.ok(clinicalNoteRepository.save(clinicalNote));
                }
            } catch (Exception ignored) {
            }
        }

        return ResponseEntity.ok().build();
    }

    @GetMapping("/client/{clientUuid}")
    public ResponseEntity<Map<String, Object>> getClientJournalBundle(
            @PathVariable UUID clientUuid,
            Authentication auth
    ) {
        boolean isTherapist = hasTherapistRole(auth);
        List<JournalEntry> entries = journalEntryRepository.findByClientUuidOrderByTimestampDesc(clientUuid);
        if (isTherapist) {
            entries = entries.stream()
                    .filter(JournalEntry::getSharedWithTherapists)
                    .collect(Collectors.toList());
        }
        List<TherapistClinicalNote> clinicalNotes = (isTherapist && clinicalNoteRepository != null)
                ? clinicalNoteRepository.findByClientUuidOrderByCreatedAtDesc(clientUuid)
                : List.of();

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("clientUuid", clientUuid);
        result.put("entries", entries);
        result.put("clinicalNotes", clinicalNotes);
        return ResponseEntity.ok(result);
    }

    @GetMapping("/history/{clientUuid}")
    public ResponseEntity<List<Map<String, String>>> getJournalCommitHistory(
            @PathVariable UUID clientUuid,
            Authentication auth
    ) {
        boolean isTherapist = hasTherapistRole(auth);
        return ResponseEntity.ok(journalGitService.getCommitHistory(clientUuid.toString(), isTherapist, journalService));
    }

    @GetMapping("/timeline/{clientUuid}")
    public ResponseEntity<List<Map<String, String>>> getClientTimeline(
        @PathVariable UUID clientUuid,
        Authentication auth
    ) {
        boolean isTherapist = hasTherapistRole(auth);

        try {
            String rawMarkdown = journalGitService.readJournalEntry(clientUuid.toString());
            String content = journalService.getJournalContent(rawMarkdown, isTherapist);
            return ResponseEntity.ok(List.of(
                Map.of("id", clientUuid.toString(), "content", content)
            ));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    private boolean hasTherapistRole(Authentication auth) {
        if (auth != null && auth.getPrincipal() instanceof Jwt jwt) {
            Map<String, Object> realmAccess = jwt.getClaimAsMap("realm_access");
            if (realmAccess != null && realmAccess.get("roles") instanceof List<?> roles) {
                return roles.contains("therapist") || roles.contains("THERAPIST");
            }
        }
        return false;
    }

    private UUID parseUuidOrDefault(String sub) {
        try {
            return UUID.fromString(sub);
        } catch (Exception ex) {
            return UUID.fromString("6ba7b810-9dad-11d1-80b4-00c04fd430c8");
        }
    }
}
