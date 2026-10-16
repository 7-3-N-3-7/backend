package com.platform.controller;

<<<<<<< HEAD
=======
<<<<<<< HEAD
<<<<<<< HEAD
<<<<<<< HEAD
=======
>>>>>>> 16f2191da8c19882528cd7f0f6ff79480a370715
>>>>>>> d6bd62f49cdd26c1f0dfc9614c1ba49cc9f83309
import com.platform.core.controller.BaseController;
import com.platform.crm.model.JournalEntry;
import com.platform.crm.model.TherapistClinicalNote;
import com.platform.crm.repository.JournalEntryRepository;
import com.platform.crm.repository.TherapistClinicalNoteRepository;
import com.platform.dto.JournalEntryDto;
import com.platform.repository.AppointmentRepository;
import com.platform.repository.HandshakeRepository;
import com.platform.service.JournalGitService;
import com.platform.service.JournalService;
import com.platform.service.PlatformCacheService;
<<<<<<< HEAD
=======
=======
<<<<<<< HEAD
=======
>>>>>>> 266b19092e0a4e40a20f5cd2eaf04be642298e92
=======
>>>>>>> 16f2191da8c19882528cd7f0f6ff79480a370715
import com.platform.crm.model.JournalEntry;
import com.platform.crm.model.TherapistNote;
import com.platform.crm.repository.JournalEntryRepository;
import com.platform.crm.repository.TherapistNoteRepository;
import com.platform.dto.JournalEntryDto;
<<<<<<< HEAD
<<<<<<< HEAD
>>>>>>> 3d3aa53f18dac79b5509f233401d317d3db0787d
=======
>>>>>>> 266b19092e0a4e40a20f5cd2eaf04be642298e92
=======
>>>>>>> 3d3aa53f18dac79b5509f233401d317d3db0787d
>>>>>>> 16f2191da8c19882528cd7f0f6ff79480a370715
>>>>>>> d6bd62f49cdd26c1f0dfc9614c1ba49cc9f83309
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
<<<<<<< HEAD
=======
<<<<<<< HEAD
<<<<<<< HEAD
<<<<<<< HEAD
=======
>>>>>>> 16f2191da8c19882528cd7f0f6ff79480a370715
>>>>>>> d6bd62f49cdd26c1f0dfc9614c1ba49cc9f83309
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Objects;
<<<<<<< HEAD
=======
=======
<<<<<<< HEAD
=======
>>>>>>> 266b19092e0a4e40a20f5cd2eaf04be642298e92
=======
>>>>>>> 16f2191da8c19882528cd7f0f6ff79480a370715
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
<<<<<<< HEAD
<<<<<<< HEAD
>>>>>>> 3d3aa53f18dac79b5509f233401d317d3db0787d
=======
>>>>>>> 266b19092e0a4e40a20f5cd2eaf04be642298e92
=======
>>>>>>> 3d3aa53f18dac79b5509f233401d317d3db0787d
>>>>>>> 16f2191da8c19882528cd7f0f6ff79480a370715
>>>>>>> d6bd62f49cdd26c1f0dfc9614c1ba49cc9f83309
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/journals")
<<<<<<< HEAD
=======
<<<<<<< HEAD
<<<<<<< HEAD
<<<<<<< HEAD
=======
>>>>>>> 16f2191da8c19882528cd7f0f6ff79480a370715
>>>>>>> d6bd62f49cdd26c1f0dfc9614c1ba49cc9f83309
@CrossOrigin(origins = "*")
public class JournalController extends BaseController<JournalEntry, JournalEntryDto, UUID> {

    private final JournalService journalService;

    public JournalController(JournalEntryRepository journalEntryRepository, JournalGitService journalGitService) {
        this(journalEntryRepository, journalGitService, null, new JournalService(), new PlatformCacheService(), null, null);
    }

    public JournalController(
            JournalEntryRepository journalEntryRepository,
            JournalGitService journalGitService,
            TherapistClinicalNoteRepository clinicalNoteRepository,
            JournalService journalService
    ) {
        this(journalEntryRepository, journalGitService, clinicalNoteRepository, journalService, new PlatformCacheService(), null, null);
    }
<<<<<<< HEAD
=======
=======
=======
public class JournalController {

>>>>>>> 3d3aa53f18dac79b5509f233401d317d3db0787d
<<<<<<< HEAD
=======
public class JournalController {

>>>>>>> 266b19092e0a4e40a20f5cd2eaf04be642298e92
=======
>>>>>>> 16f2191da8c19882528cd7f0f6ff79480a370715
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
<<<<<<< HEAD
<<<<<<< HEAD
<<<<<<< HEAD
=======
>>>>>>> 16f2191da8c19882528cd7f0f6ff79480a370715
>>>>>>> fa4972dbc8a433d3937f53daa44e44c3ed6b905a
>>>>>>> d6bd62f49cdd26c1f0dfc9614c1ba49cc9f83309

    @Autowired
    public JournalController(
            JournalEntryRepository journalEntryRepository,
            JournalGitService journalGitService,
            TherapistClinicalNoteRepository clinicalNoteRepository,
            JournalService journalService,
            PlatformCacheService cacheService,
            @Autowired(required = false) AppointmentRepository appointmentRepository,
            @Autowired(required = false) HandshakeRepository handshakeRepository
    ) {
        super(Objects.requireNonNullElseGet(journalService, JournalService::new));
        this.journalService = (JournalService) this.service;
        this.journalService.withCollaborators(
                journalEntryRepository,
                journalGitService,
                clinicalNoteRepository,
                Objects.requireNonNullElseGet(cacheService, PlatformCacheService::new),
                appointmentRepository,
                handshakeRepository
        );
    }

    @Override
    @PostMapping
    public ResponseEntity<JournalEntry> create(@RequestBody JournalEntryDto dto) {
        return createJournalEntry(dto, org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication());
    }

    public ResponseEntity<JournalEntry> createJournalEntry(@RequestBody JournalEntryDto dto, Authentication authentication) {
        return ResponseEntity.ok(journalService.createJournalEntry(dto, authentication));
    }

    @PatchMapping("/{entryId}/sharing")
    public ResponseEntity<JournalEntry> updateJournalEntrySharing(
            @PathVariable UUID entryId,
            @RequestBody Map<String, Object> payload,
            Authentication authentication
    ) {
        return ResponseEntity.ok(journalService.updateJournalEntrySharing(entryId, payload));
<<<<<<< HEAD
=======
=======
=======
<<<<<<< HEAD
=======
>>>>>>> 266b19092e0a4e40a20f5cd2eaf04be642298e92
=======
>>>>>>> 16f2191da8c19882528cd7f0f6ff79480a370715

        JournalEntry entry = new JournalEntry(
                dto.getTitle(),
                dto.getContent(),
                dto.getMoodRating(),
                LocalDateTime.now(),
                clientUuid
        );

<<<<<<< HEAD
<<<<<<< HEAD
>>>>>>> 3d3aa53f18dac79b5509f233401d317d3db0787d
=======
>>>>>>> 266b19092e0a4e40a20f5cd2eaf04be642298e92
=======
>>>>>>> 3d3aa53f18dac79b5509f233401d317d3db0787d
>>>>>>> 16f2191da8c19882528cd7f0f6ff79480a370715
        JournalEntry saved = journalEntryRepository.save(entry);
        
        // Write to Git for audit trail
        String commitMsg = "Added journal entry: " + dto.getTitle();
        String content = "Title: " + dto.getTitle() + "\nMood: " + dto.getMoodRating() + "\nContent: " + dto.getContent();
        journalGitService.commitJournalEntry(clientUuid.toString(), content, commitMsg);
        
        return ResponseEntity.ok(saved);
<<<<<<< HEAD
<<<<<<< HEAD
<<<<<<< HEAD
=======
>>>>>>> 16f2191da8c19882528cd7f0f6ff79480a370715
>>>>>>> fa4972dbc8a433d3937f53daa44e44c3ed6b905a
>>>>>>> d6bd62f49cdd26c1f0dfc9614c1ba49cc9f83309
    }

    @PostMapping("/therapist-notes")
    public ResponseEntity<?> createTherapistNote(@RequestBody Map<String, Object> payload, Authentication authentication) {
        TherapistClinicalNote savedNote = journalService.createTherapistNote(payload, authentication);
        if (savedNote != null) {
            return ResponseEntity.ok(savedNote);
        }
        return ResponseEntity.ok().build();
<<<<<<< HEAD
=======
=======
=======
<<<<<<< HEAD
=======
>>>>>>> 266b19092e0a4e40a20f5cd2eaf04be642298e92
=======
>>>>>>> 16f2191da8c19882528cd7f0f6ff79480a370715
    }

    @PostMapping("/therapist-notes")
    public ResponseEntity<?> createTherapistNote(@RequestBody java.util.Map<String, Object> payload, Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof Jwt jwt)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Unauthorized");
        }
<<<<<<< HEAD
<<<<<<< HEAD
>>>>>>> 3d3aa53f18dac79b5509f233401d317d3db0787d
=======
>>>>>>> 266b19092e0a4e40a20f5cd2eaf04be642298e92
=======
>>>>>>> 3d3aa53f18dac79b5509f233401d317d3db0787d
>>>>>>> 16f2191da8c19882528cd7f0f6ff79480a370715
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
<<<<<<< HEAD
<<<<<<< HEAD
<<<<<<< HEAD
=======
>>>>>>> 16f2191da8c19882528cd7f0f6ff79480a370715
>>>>>>> fa4972dbc8a433d3937f53daa44e44c3ed6b905a
>>>>>>> d6bd62f49cdd26c1f0dfc9614c1ba49cc9f83309
    }

    @GetMapping("/client/{clientUuid}")
    public ResponseEntity<Map<String, Object>> getClientJournalBundle(
            @PathVariable UUID clientUuid,
            @RequestParam(required = false) UUID therapistUuid,
            Authentication auth
    ) {
        return ResponseEntity.ok(journalService.getClientJournalBundle(clientUuid, therapistUuid, auth));
    }
<<<<<<< HEAD
=======
=======
=======
<<<<<<< HEAD
=======
>>>>>>> 266b19092e0a4e40a20f5cd2eaf04be642298e92
=======
>>>>>>> 16f2191da8c19882528cd7f0f6ff79480a370715
    }

    @GetMapping("/timeline/{clientUuid}")
    public ResponseEntity<java.util.List<java.util.Map<String, String>>> getClientTimeline(
        @PathVariable UUID clientUuid,
        Authentication auth
    ) {
<<<<<<< HEAD
<<<<<<< HEAD
>>>>>>> 3d3aa53f18dac79b5509f233401d317d3db0787d
=======
>>>>>>> 266b19092e0a4e40a20f5cd2eaf04be642298e92
=======
>>>>>>> 3d3aa53f18dac79b5509f233401d317d3db0787d
>>>>>>> 16f2191da8c19882528cd7f0f6ff79480a370715
        boolean isTherapist = false;
        if (auth != null && auth.getPrincipal() instanceof Jwt jwt) {
            if (hasRole(jwt, "therapist")) {
                isTherapist = true;
            }
        }
<<<<<<< HEAD
<<<<<<< HEAD
<<<<<<< HEAD
=======
>>>>>>> 16f2191da8c19882528cd7f0f6ff79480a370715
>>>>>>> fa4972dbc8a433d3937f53daa44e44c3ed6b905a
>>>>>>> d6bd62f49cdd26c1f0dfc9614c1ba49cc9f83309

    @GetMapping("/history/{clientUuid}")
    public ResponseEntity<List<Map<String, String>>> getJournalCommitHistory(
            @PathVariable UUID clientUuid,
            @RequestParam(required = false) UUID therapistUuid,
            Authentication auth
    ) {
        return ResponseEntity.ok(journalService.getJournalCommitHistory(clientUuid, therapistUuid, auth));
    }

    public ResponseEntity<Map<String, Object>> getClientJournalBundle(
            UUID clientUuid,
            Authentication auth
    ) {
        return getClientJournalBundle(clientUuid, null, auth);
    }

    public ResponseEntity<List<Map<String, String>>> getJournalCommitHistory(
            UUID clientUuid,
            Authentication auth
    ) {
        return getJournalCommitHistory(clientUuid, null, auth);
    }

    @GetMapping("/timeline/{clientUuid}")
    public ResponseEntity<List<Map<String, String>>> getClientTimeline(
            @PathVariable UUID clientUuid,
            Authentication auth
    ) {
        try {
            return ResponseEntity.ok(journalService.getClientTimeline(clientUuid, auth));
<<<<<<< HEAD
=======
=======
<<<<<<< HEAD
=======
>>>>>>> 266b19092e0a4e40a20f5cd2eaf04be642298e92
=======
>>>>>>> 16f2191da8c19882528cd7f0f6ff79480a370715

        try {
            String rawMarkdown = journalGitService.readJournalEntry(clientUuid.toString());
            String content = new com.platform.service.JournalService().getJournalContent(rawMarkdown, isTherapist);
            return ResponseEntity.ok(java.util.List.of(
                java.util.Map.of("id", clientUuid.toString(), "content", content)
            ));
<<<<<<< HEAD
<<<<<<< HEAD
>>>>>>> 3d3aa53f18dac79b5509f233401d317d3db0787d
=======
>>>>>>> 266b19092e0a4e40a20f5cd2eaf04be642298e92
=======
>>>>>>> 3d3aa53f18dac79b5509f233401d317d3db0787d
>>>>>>> 16f2191da8c19882528cd7f0f6ff79480a370715
>>>>>>> d6bd62f49cdd26c1f0dfc9614c1ba49cc9f83309
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
}
