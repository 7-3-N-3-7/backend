package com.platform.controller;

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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/journals")
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
    }

    @PostMapping("/therapist-notes")
    public ResponseEntity<?> createTherapistNote(@RequestBody Map<String, Object> payload, Authentication authentication) {
        TherapistClinicalNote savedNote = journalService.createTherapistNote(payload, authentication);
        if (savedNote != null) {
            return ResponseEntity.ok(savedNote);
        }
        return ResponseEntity.ok().build();
    }

    @GetMapping("/client/{clientUuid}")
    public ResponseEntity<Map<String, Object>> getClientJournalBundle(
            @PathVariable UUID clientUuid,
            @RequestParam(required = false) UUID therapistUuid,
            Authentication auth
    ) {
        return ResponseEntity.ok(journalService.getClientJournalBundle(clientUuid, therapistUuid, auth));
    }

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
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
}
