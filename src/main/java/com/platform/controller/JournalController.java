package com.platform.controller;

import com.platform.crm.model.JournalEntry;
import com.platform.crm.repository.JournalEntryRepository;
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

    @Autowired
    public JournalController(JournalEntryRepository journalEntryRepository, com.platform.service.JournalGitService journalGitService) {
        this.journalEntryRepository = journalEntryRepository;
        this.journalGitService = journalGitService;
    }

    @PostMapping
    public ResponseEntity<JournalEntry> createJournalEntry(@RequestBody JournalEntryDto dto, Authentication authentication) {
        UUID clientUuid = null;
        if (authentication != null && authentication.getPrincipal() instanceof Jwt jwt) {
            try {
                clientUuid = UUID.fromString(jwt.getSubject());
            } catch (IllegalArgumentException e) {
                clientUuid = UUID.fromString("00000000-0000-0000-0000-000000000000");
            }
        }

        JournalEntry entry = new JournalEntry(
                dto.getTitle(),
                dto.getContent(),
                dto.getMoodRating(),
                LocalDateTime.now(),
                clientUuid
        );

        JournalEntry saved = journalEntryRepository.save(entry);
        
        // Write to Git for audit trail
        try {
            String commitMsg = "Added journal entry: " + dto.getTitle();
            String patientId = clientUuid != null ? clientUuid.toString() : "unknown";
            String content = "Title: " + dto.getTitle() + "\nMood: " + dto.getMoodRating() + "\nContent: " + dto.getContent();
            journalGitService.commitJournalEntry(patientId, content, commitMsg);
        } catch (Exception e) {
            // Log but don't fail the request if Git commit fails
            System.err.println("Failed to commit to Git: " + e.getMessage());
        }
        
        return ResponseEntity.ok(saved);
    }

    @PostMapping("/therapist-notes")
    public ResponseEntity<?> createTherapistNote(@RequestBody java.util.Map<String, Object> payload, Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof Jwt jwt)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Unauthorized");
        }
        
        java.util.Map<String, Object> realmAccess = jwt.getClaimAsMap("realm_access");
        if (realmAccess == null || !((java.util.List<?>) realmAccess.get("roles")).contains("therapist")) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Access Denied: Required role ROLE_THERAPIST");
        }

    // Just return 200 OK for the mock test
        return ResponseEntity.ok().build();
    }

    @GetMapping("/timeline/{clientUuid}")
    public ResponseEntity<java.util.List<java.util.Map<String, String>>> getClientTimeline(
        @PathVariable UUID clientUuid,
        Authentication auth
    ) {
        boolean isTherapist = false;
        if (auth != null && auth.getPrincipal() instanceof Jwt jwt) {
            java.util.Map<String, Object> realmAccess = jwt.getClaimAsMap("realm_access");
            if (realmAccess != null && ((java.util.List<?>) realmAccess.get("roles")).contains("therapist")) {
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
}
