package com.platform.controller;

import com.platform.crm.model.JournalEntry;
import com.platform.crm.model.TherapistNote;
import com.platform.crm.repository.JournalEntryRepository;
import com.platform.crm.repository.TherapistNoteRepository;
import com.platform.dto.JournalEntryDto;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/journals")
public class JournalController {

    private final JournalEntryRepository journalEntryRepository;
    private final com.platform.service.JournalGitService journalGitService;
    private final TherapistNoteRepository therapistNoteRepository;

    @Autowired
    public JournalController(JournalEntryRepository journalEntryRepository,
                             com.platform.service.JournalGitService journalGitService,
                             TherapistNoteRepository therapistNoteRepository) {
        this.journalEntryRepository = journalEntryRepository;
        this.journalGitService = journalGitService;
        this.therapistNoteRepository = therapistNoteRepository;
    }

    @PostMapping
    public ResponseEntity<JournalEntry> createJournalEntry(@RequestBody JournalEntryDto dto, Authentication authentication) {
        UUID clientUuid = getCallerUuid(authentication);

        JournalEntry entry = new JournalEntry(
                dto.getTitle(),
                dto.getContent(),
                dto.getMoodRating(),
                LocalDateTime.now(),
                clientUuid
        );

        JournalEntry saved = journalEntryRepository.save(entry);
        
        // Write to Git for audit trail
        String commitMsg = "Added journal entry: " + dto.getTitle();
        String content = "Title: " + dto.getTitle() + "\nMood: " + dto.getMoodRating() + "\nContent: " + dto.getContent();
        journalGitService.commitJournalEntry(clientUuid.toString(), content, commitMsg);
        
        return ResponseEntity.ok(saved);
    }

    @PostMapping("/therapist-notes")
    public ResponseEntity<?> createTherapistNote(@RequestBody java.util.Map<String, Object> payload, Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof Jwt jwt)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Unauthorized");
        }
        if (!hasRole(jwt, "therapist")) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body("Access Denied: Required role ROLE_THERAPIST");
        }
        UUID clientUuid;
        Object rawClientUuid = payload.get("clientUuid");
        Object rawNote = payload.get("therapistNote");
        try {
            clientUuid = UUID.fromString(String.valueOf(rawClientUuid));
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid client UUID");
        }
        if (!(rawNote instanceof String note) || note.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Therapist note is required");
        }
        TherapistNote saved = therapistNoteRepository.save(
                new TherapistNote(clientUuid, getCallerUuid(authentication), note));
        return ResponseEntity.ok(saved);
    }

    @GetMapping("/timeline/{clientUuid}")
    public ResponseEntity<java.util.List<java.util.Map<String, String>>> getClientTimeline(
        @PathVariable UUID clientUuid,
        Authentication auth
    ) {
        boolean isTherapist = false;
        if (auth != null && auth.getPrincipal() instanceof Jwt jwt) {
            if (hasRole(jwt, "therapist")) {
                isTherapist = true;
            }
        }

        try {
            String rawMarkdown = journalGitService.readJournalEntry(clientUuid.toString());
            String content = new com.platform.service.JournalService().getJournalContent(rawMarkdown, isTherapist);
            return ResponseEntity.ok(java.util.List.of(
                java.util.Map.of("id", clientUuid.toString(), "content", content)
            ));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    private UUID getCallerUuid(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof Jwt jwt)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Unauthorized");
        }
        try {
            return UUID.fromString(jwt.getSubject());
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid user identity");
        }
    }

    private boolean hasRole(Jwt jwt, String role) {
        java.util.Map<String, Object> realmAccess = jwt.getClaimAsMap("realm_access");
        Object roles = realmAccess == null ? null : realmAccess.get("roles");
        return roles instanceof java.util.List<?> roleList
                && roleList.stream().anyMatch(role::equals);
    }
}
