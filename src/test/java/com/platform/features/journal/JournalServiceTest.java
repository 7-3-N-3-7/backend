package com.platform.features.journal;

import com.platform.config.PlatformCacheService;
import com.platform.core.BaseDomainCore.DomainException;
import com.platform.features.appointment.AppointmentFeature.Appointment;
import com.platform.features.appointment.AppointmentFeature.AppointmentPort;
import com.platform.features.clinic.ClinicPlatformFeature.ClientTherapistHandshake;
import com.platform.features.clinic.ClinicPlatformFeature.HandshakePort;
import com.platform.features.journal.JournalFeature.JournalController;
import com.platform.features.journal.JournalFeature.JournalEntry;
import com.platform.features.journal.JournalFeature.JournalEntryDto;
import com.platform.features.journal.JournalFeature.JournalEntryRepository;
import com.platform.features.journal.JournalFeature.JournalGitService;
import com.platform.features.journal.JournalFeature.JournalService;
import com.platform.features.journal.JournalFeature.TherapistClinicalNote;
import com.platform.features.journal.JournalFeature.TherapistClinicalNoteDto;
import com.platform.features.journal.JournalFeature.TherapistClinicalNoteRepository;
import com.platform.features.journal.JournalFeature.TherapistNote;
import com.platform.features.journal.JournalFeature.TherapistNoteDto;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;

public class JournalServiceTest {

    @Test
    public void testJournalGitServiceAndControllerBundle() throws Exception {
        Path tempRepo = Files.createTempDirectory("jgit-test-repo");
        JournalGitService gitService = new JournalGitService();
        ReflectionTestUtils.setField(gitService, "repoPath", tempRepo.toString());
        gitService.init();

        JournalEntryRepository entryRepo = Mockito.mock(JournalEntryRepository.class);
        TherapistClinicalNoteRepository noteRepo = Mockito.mock(TherapistClinicalNoteRepository.class);
        Mockito.when(entryRepo.save(any(JournalEntry.class))).thenAnswer(i -> i.getArgument(0));
        Mockito.when(noteRepo.save(any(TherapistClinicalNote.class))).thenAnswer(i -> i.getArgument(0));

        JournalService journalService = new JournalService(entryRepo, gitService, noteRepo, new PlatformCacheService(), null, null);
        JournalController journalController = new JournalController(journalService);

        UUID clientUuid = UUID.randomUUID();
        Jwt clientJwt = Jwt.withTokenValue("token")
                .header("alg", "none")
                .claim("sub", clientUuid.toString())
                .claim("preferred_username", "anna_client")
                .claim("realm_access", Map.of("roles", List.of("client")))
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(3600))
                .build();
        Authentication clientAuth = Mockito.mock(Authentication.class);
        Mockito.when(clientAuth.getPrincipal()).thenReturn(clientJwt);

        JournalEntryDto dto = new JournalEntryDto(
                null,
                "Uge 38 Tanker",
                "Jeg har sovet bedre i denne uge.",
                8,
                true,
                null,
                null,
                null,
                "main"
        );

        ResponseEntity<JournalEntry> savedEntry = journalController.createJournalEntry(dto, clientAuth);
        Assertions.assertEquals(HttpStatus.OK, savedEntry.getStatusCode());
        Assertions.assertNotNull(savedEntry.getBody().getGitCommitHash());

        // Test @PostMapping create(JournalEntryDto) using SecurityContextHolder
        SecurityContextHolder.getContext().setAuthentication(clientAuth);
        ResponseEntity<JournalEntryDto> createdViaBaseOverride = journalController.create(dto);
        Assertions.assertEquals(HttpStatus.OK, createdViaBaseOverride.getStatusCode());
        Assertions.assertEquals("Uge 38 Tanker", createdViaBaseOverride.getBody().getTitle());
        SecurityContextHolder.clearContext();

        // Therapist adds confidential note
        Jwt therapistJwt = Jwt.withTokenValue("therapist-token")
                .header("alg", "none")
                .claim("sub", UUID.randomUUID().toString())
                .claim("realm_access", Map.of("roles", List.of("therapist")))
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(3600))
                .build();
        Authentication therapistAuth = Mockito.mock(Authentication.class);
        Mockito.when(therapistAuth.getPrincipal()).thenReturn(therapistJwt);

        Map<String, Object> notePayload = new HashMap<>();
        notePayload.put("clientUuid", clientUuid.toString());
        notePayload.put("therapistNote", "Patient viser god fremgang i eksponeringsterapi.");
        notePayload.put("therapistName", "Dr. Maja Holm");
        notePayload.put("organisationName", "Nordic Mind Clinic");

        ResponseEntity<?> noteResp = journalController.createTherapistNote(notePayload, therapistAuth);
        Assertions.assertEquals(HttpStatus.OK, noteResp.getStatusCode());

        // Verify commit history for Therapist vs Client (Client view strips INTERNAL_MEDICAL_NOTES)
        List<Map<String, String>> therapistHistory = journalController.getJournalCommitHistory(clientUuid, therapistAuth).getBody();
        List<Map<String, String>> clientHistory = journalController.getJournalCommitHistory(clientUuid, clientAuth).getBody();
        Assertions.assertFalse(therapistHistory.isEmpty());
        Assertions.assertTrue(therapistHistory.get(0).get("snapshot").contains("INTERNAL_MEDICAL_NOTES"));
        Assertions.assertFalse(clientHistory.get(0).get("snapshot").contains("INTERNAL_MEDICAL_NOTES"));

        // Verify bundle endpoint and TherapistClinicalNote model accessors
        TherapistClinicalNote noteModel = new TherapistClinicalNote();
        noteModel.setId(UUID.randomUUID());
        noteModel.setClientUuid(clientUuid);
        noteModel.setAuthorTherapistUuid(UUID.randomUUID());
        noteModel.setAuthorTherapistName("Dr. Holm");
        noteModel.setOrganisationName("Clinic");
        noteModel.setJournalEntryId(UUID.randomUUID());
        noteModel.setAppointmentId(UUID.randomUUID());
        noteModel.setConfidentialMarkdown("Confidential");
        noteModel.setGitCommitHash("abc12345");
        noteModel.setCreatedAt(LocalDateTime.now());
        Assertions.assertEquals("Dr. Holm", noteModel.getAuthorTherapistName());
        Assertions.assertEquals("Clinic", noteModel.getOrganisationName());
        Assertions.assertEquals("Confidential", noteModel.getConfidentialMarkdown());
        Assertions.assertEquals("abc12345", noteModel.getGitCommitHash());
        Assertions.assertNotNull(noteModel.getId());
        Assertions.assertNotNull(noteModel.getClientUuid());
        Assertions.assertNotNull(noteModel.getAuthorTherapistUuid());
        Assertions.assertNotNull(noteModel.getJournalEntryId());
        Assertions.assertNotNull(noteModel.getAppointmentId());
        Assertions.assertNotNull(noteModel.getCreatedAt());

        JournalEntry privateEntry = new JournalEntry("Privat", "Kun til mig", 6, LocalDateTime.now(), clientUuid);
        privateEntry.setSharedWithTherapists(false);
        Mockito.when(entryRepo.findByClientUuidOrderByTimestampDesc(clientUuid))
                .thenReturn(List.of(savedEntry.getBody(), privateEntry));
        Mockito.when(noteRepo.findByClientUuidOrderByCreatedAtDesc(clientUuid))
                .thenReturn(List.of(noteModel));

        Map<String, Object> therapistBundle = journalController.getClientJournalBundle(clientUuid, therapistAuth).getBody();
        List<?> visibleToTherapist = (List<?>) therapistBundle.get("entries");
        Assertions.assertEquals(1, visibleToTherapist.size());

        Map<String, Object> clientBundle = journalController.getClientJournalBundle(clientUuid, clientAuth).getBody();
        List<?> visibleToClient = (List<?>) clientBundle.get("entries");
        Assertions.assertEquals(2, visibleToClient.size());

        // Re-init on existing repo and read
        gitService.init();
        Assertions.assertFalse(gitService.readJournalEntry(clientUuid.toString()).isEmpty());
        Assertions.assertTrue(gitService.readJournalEntry("non-existent-id").isEmpty());
        Assertions.assertTrue(gitService.getCommitHistory("non-existent-id", true, journalService::getJournalContent).isEmpty());
        Assertions.assertNotNull(gitService.getGit());

        // Test appointment privacy: appointment commits must NOT be exposed to therapists,
        // but journal entries referencing appointments MUST remain visible.
        gitService.commitJournalEntryWithHash(
                clientUuid.toString(),
                "## Aftale: Samtale 1\nTidspunkt: 10:00\nRefs-Appointment: #apt-123",
                "Aftale oprettet: Samtale 1",
                "Klinik Booking"
        );
        gitService.commitJournalEntryWithHash(
                clientUuid.toString(),
                "## Klient Refleksion\nRefs-Appointment: #apt-123\nJeg glæder mig til samtalen.",
                "Added journal entry: Klient Refleksion",
                "Anders Sand"
        );

        List<Map<String, String>> historyForClient = gitService.getCommitHistory(clientUuid.toString(), false, journalService::getJournalContent);
        List<Map<String, String>> historyForTherapist = gitService.getCommitHistory(clientUuid.toString(), true, journalService::getJournalContent);

        // Client sees both appointment commit and journal commit
        Assertions.assertTrue(historyForClient.stream().anyMatch(c -> c.get("message").contains("Aftale oprettet")));
        Assertions.assertTrue(historyForClient.stream().anyMatch(c -> c.get("message").contains("Added journal entry")));

        // Therapist must NEVER see the raw appointment commit
        Assertions.assertFalse(historyForTherapist.stream().anyMatch(c -> c.get("message").contains("Aftale oprettet")));
        // But therapist CAN see the client journal entry referencing the appointment
        Assertions.assertTrue(historyForTherapist.stream().anyMatch(c -> c.get("message").contains("Added journal entry")));

        // Test invalid UUID subject in parseUuidOrDefault with Keycloak THERAPIST role
        Jwt nonUuidSubJwt = Jwt.withTokenValue("non-uuid")
                .header("alg", "none")
                .claim("sub", "not-a-uuid")
                .claim("realm_access", Map.of("roles", List.of("THERAPIST")))
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(3600))
                .build();
        Authentication nonUuidAuth = Mockito.mock(Authentication.class);
        Mockito.when(nonUuidAuth.getPrincipal()).thenReturn(nonUuidSubJwt);
        ResponseEntity<?> noteWithDefaultUuid = journalController.createTherapistNote(notePayload, nonUuidAuth);
        Assertions.assertEquals(HttpStatus.OK, noteWithDefaultUuid.getStatusCode());

        Jwt nonUuidClientJwt = Jwt.withTokenValue("non-uuid-client")
                .header("alg", "none")
                .claim("sub", "not-a-uuid")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(3600))
                .build();
        Authentication nonUuidClientAuth = Mockito.mock(Authentication.class);
        Mockito.when(nonUuidClientAuth.getPrincipal()).thenReturn(nonUuidClientJwt);
        Assertions.assertNotNull(journalController.getClientJournalBundle(clientUuid, nonUuidClientAuth).getBody());

        // Test per-therapist sharing & individual entry sharing update (PATCH /{entryId}/sharing)
        UUID specificTherapistUuid = UUID.randomUUID();
        UUID otherTherapistUuid = UUID.randomUUID();
        UUID entryId = UUID.randomUUID();
        savedEntry.getBody().setId(entryId);
        savedEntry.getBody().setSharedTherapistUuids(List.of(specificTherapistUuid));

        Assertions.assertTrue(savedEntry.getBody().isVisibleToTherapist(specificTherapistUuid));
        Assertions.assertFalse(savedEntry.getBody().isVisibleToTherapist(otherTherapistUuid));
        Assertions.assertFalse(privateEntry.isVisibleToTherapist(specificTherapistUuid));

        Mockito.when(entryRepo.findById(entryId)).thenReturn(Optional.of(savedEntry.getBody()));
        ResponseEntity<JournalEntry> updatedSharing = journalController.updateJournalEntrySharing(
                entryId,
                Map.of("sharedWithTherapists", true, "sharedTherapistUuids", List.of(specificTherapistUuid.toString(), "invalid-uuid")),
                clientAuth
        );
        Assertions.assertEquals(HttpStatus.OK, updatedSharing.getStatusCode());
        Assertions.assertEquals(List.of(specificTherapistUuid), updatedSharing.getBody().getSharedTherapistUuids());

        // Make private via PATCH
        ResponseEntity<JournalEntry> madePrivate = journalController.updateJournalEntrySharing(
                entryId,
                Map.of("sharedWithTherapists", false, "sharedTherapistUuids", "none"),
                clientAuth
        );
        Assertions.assertFalse(madePrivate.getBody().getSharedWithTherapists());
        Assertions.assertTrue(madePrivate.getBody().getSharedTherapistUuids().isEmpty());

        // Restore shared for bundle test
        savedEntry.getBody().setSharedWithTherapists(true);
        savedEntry.getBody().setSharedTherapistUuids(List.of());
        Assertions.assertThrows(
                DomainException.class,
                () -> journalController.updateJournalEntrySharing(UUID.randomUUID(), Map.of("sharedWithTherapists", false), clientAuth)
        );

        // Test JournalService 1-arg constructor and JournalController with null auth, 401/403 on createTherapistNote, blank payload, and timeline
        JournalEntryDto dtoWithSharedUuids = new JournalEntryDto(
                null,
                "Uge 38 Tanker",
                "Jeg har sovet bedre i denne uge.",
                8,
                true,
                List.of(specificTherapistUuid),
                null,
                null,
                "main"
        );
        JournalService oneArgJs = new JournalService(entryRepo);
        Assertions.assertNull(oneArgJs.toDto(null));
        JournalController twoArgJc = new JournalController( new JournalService(entryRepo, gitService, null, new PlatformCacheService(), null, null));
        Assertions.assertEquals(HttpStatus.OK, twoArgJc.createJournalEntry(dtoWithSharedUuids, null).getStatusCode());
        Assertions.assertEquals(HttpStatus.OK, twoArgJc.createJournalEntry(dtoWithSharedUuids, nonUuidClientAuth).getStatusCode());
        Assertions.assertThrows(DomainException.class, () -> twoArgJc.createTherapistNote(notePayload, null));
        Assertions.assertThrows(DomainException.class, () -> twoArgJc.createTherapistNote(notePayload, clientAuth));
        Assertions.assertEquals(HttpStatus.OK, twoArgJc.createTherapistNote(Map.of(), therapistAuth).getStatusCode());
        Assertions.assertEquals(HttpStatus.OK, twoArgJc.createTherapistNote(notePayload, therapistAuth).getStatusCode());
        Assertions.assertEquals(HttpStatus.OK, twoArgJc.getClientTimeline(clientUuid, therapistAuth).getStatusCode());
        Assertions.assertEquals(HttpStatus.OK, twoArgJc.getClientJournalBundle(clientUuid, null).getStatusCode());

        // Timeline error branch (500) when gitService is null
        JournalController nullGitCtrl = new JournalController(oneArgJs);
        Assertions.assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, nullGitCtrl.getClientTimeline(clientUuid, therapistAuth).getStatusCode());

        gitService.close();
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testJournalBranchesHandshakesAndModels() throws Exception {
        UUID therapistUuid = UUID.randomUUID();
        UUID clientUuid = UUID.randomUUID();

        // 1. JournalFeature.filterInternalNotes
        JournalService journalService = new JournalService(Mockito.mock(JournalEntryRepository.class));
        String mixedMarkdown = "## Client Journal Entry (2026-04-14T15:39:11Z)\nClient initial note\n\n"
                + "<!-- INTERNAL_MEDICAL_NOTES -->\n### Fortroligt Klinisk Notat (2026-04-14T16:12:48Z)\nSecret note 1\n\n"
                + "## Terapeutisk opfølgning (Delt med klient) (2026-04-14T20:42:08Z)\nAnders skal kunne se den her besked\n\n"
                + "<!-- INTERNAL_MEDICAL_NOTES -->\n### Fortroligt Klinisk Notat (2026-04-14T20:42:22Z)\nAnder skal IKKE kunne se den her besked\n";

        String clientVisibleMarkdown = journalService.getJournalContent(mixedMarkdown, false);
        Assertions.assertTrue(clientVisibleMarkdown.contains("Client initial note"));
        Assertions.assertTrue(clientVisibleMarkdown.contains("Anders skal kunne se den her besked"));
        Assertions.assertFalse(clientVisibleMarkdown.contains("Secret note 1"));
        Assertions.assertFalse(clientVisibleMarkdown.contains("Ander skal IKKE kunne se den her besked"));

        String therapistVisibleMarkdown = journalService.getJournalContent(mixedMarkdown, true);
        Assertions.assertTrue(therapistVisibleMarkdown.contains("Anders skal kunne se den her besked"));
        Assertions.assertTrue(therapistVisibleMarkdown.contains("Ander skal IKKE kunne se den her besked"));
        Assertions.assertEquals("", journalService.getJournalContent(null, true));
        Assertions.assertNull(journalService.toEntity(null));

        // 2. JournalEntry & TherapistNote & TherapistClinicalNote & JournalEntryDto models
        JournalEntry je = new JournalEntry();
        je.setId(UUID.randomUUID());
        je.setTitle("Title");
        je.setContent("Content");
        je.setMoodRating(5);
        je.setClientUuid(clientUuid);
        je.setGitCommitHash("hash1");
        je.setAppointmentId(UUID.randomUUID());
        ReflectionTestUtils.setField(je, "timestamp", null);
        ReflectionTestUtils.setField(je, "sharedWithTherapists", null);
        je.onCreate();
        Assertions.assertNotNull(je.getTimestamp());
        Assertions.assertTrue(je.getSharedWithTherapists());
        LocalDateTime customTs = LocalDateTime.now().minusDays(1);
        je.setTimestamp(customTs);
        Assertions.assertEquals(customTs, je.getTimestamp());
        Assertions.assertEquals("Title", je.getTitle());
        Assertions.assertEquals("Content", je.getContent());
        Assertions.assertEquals(5, je.getMoodRating());
        Assertions.assertEquals(clientUuid, je.getClientUuid());

        ReflectionTestUtils.setField(je, "sharedWithTherapists", null);
        Assertions.assertTrue(je.getSharedWithTherapists());

        je.setSharedTherapistUuids(null);
        Assertions.assertTrue(je.getSharedTherapistUuids().isEmpty());
        je.setSharedTherapistUuids(List.of());
        Assertions.assertTrue(je.getSharedTherapistUuids().isEmpty());

        ReflectionTestUtils.setField(je, "sharedTherapistUuidsCsv", " , " + therapistUuid + " , invalid-uuid ");
        Assertions.assertEquals(List.of(therapistUuid), je.getSharedTherapistUuids());
        Assertions.assertFalse(je.isVisibleToTherapist(null));
        Assertions.assertTrue(je.isVisibleToTherapist(therapistUuid));

        JournalEntry jePopulated = new JournalEntry("T", "C", 5, LocalDateTime.now(), clientUuid);
        jePopulated.setSharedWithTherapists(false);
        jePopulated.onCreate();
        ReflectionTestUtils.setField(jePopulated, "sharedTherapistUuidsCsv", "   ");
        Assertions.assertTrue(jePopulated.getSharedTherapistUuids().isEmpty());

        JournalEntry branchJe = new JournalEntry();
        branchJe.setGitBranch(null);
        Assertions.assertEquals("main", branchJe.getGitBranch());
        branchJe.setGitBranch("   ");
        Assertions.assertEquals("main", branchJe.getGitBranch());
        branchJe.setGitBranch(" branch-emdr ");
        Assertions.assertEquals("branch-emdr", branchJe.getGitBranch());
        ReflectionTestUtils.setField(branchJe, "gitBranch", null);
        Assertions.assertEquals("main", branchJe.getGitBranch());
        ReflectionTestUtils.setField(branchJe, "gitBranch", "   ");
        Assertions.assertEquals("main", branchJe.getGitBranch());

        JournalEntryDto branchDto = new JournalEntryDto(
                UUID.randomUUID(), "T", "C", 8, null, List.of(), clientUuid, UUID.randomUUID(), "   ", LocalDateTime.now(), "hash"
        );
        Assertions.assertTrue(branchDto.getSharedWithTherapists());
        Assertions.assertEquals("main", branchDto.getGitBranch());
        Assertions.assertNotNull(branchDto.getTimestamp());
        Assertions.assertEquals("hash", branchDto.getGitCommitHash());
        JournalEntryDto branchDtoFalse = new JournalEntryDto(
                null, "T", "C", 8, false, null, clientUuid, null, " branch-cbt "
        );
        Assertions.assertFalse(branchDtoFalse.getSharedWithTherapists());
        Assertions.assertEquals("branch-cbt", branchDtoFalse.getGitBranch());
        JournalEntryDto branchDtoNullBranch = new JournalEntryDto(
                null, "T", "C", 8, true, null, clientUuid, null, null
        );
        Assertions.assertEquals("main", branchDtoNullBranch.getGitBranch());

        TherapistNote tn = new TherapistNote(clientUuid, therapistUuid, "Note text");
        tn.onCreate();
        Assertions.assertNotNull(tn.getTimestamp());
        tn.onCreate();
        tn.setId(clientUuid);
        Assertions.assertEquals(clientUuid, tn.getId());
        Assertions.assertEquals(clientUuid, tn.getClientUuid());
        Assertions.assertEquals(therapistUuid, tn.getTherapistUuid());
        Assertions.assertEquals("Note text", tn.getNote());
        TherapistNote emptyTn = new TherapistNote();
        Assertions.assertNotNull(emptyTn);

        TherapistClinicalNote tcn = new TherapistClinicalNote(clientUuid, therapistUuid, "Dr. A", "Org", UUID.randomUUID(), "Markdown", "hash");
        tcn.onCreate();
        ReflectionTestUtils.setField(tcn, "createdAt", null);
        tcn.onCreate();
        Assertions.assertNotNull(tcn.getCreatedAt());

        // 3. JournalGitService branches
        Path tempGitDir = Files.createTempDirectory("jgit-branch-cov");
        JournalGitService jgit = new JournalGitService();
        ReflectionTestUtils.setField(jgit, "repoPath", tempGitDir.toString());
        jgit.init();
        jgit.commitJournalEntry(clientUuid.toString(), "Initial audit", "Audit commit");

        String[] filteredMessages = new String[]{
                "Aftale oprettet: 1",
                "Aftale ombooket: 2",
                "Aftale aflyst: 3",
                "Aftale: 4",
                "Appointment: 5",
                "Appointment created: 6",
                "Appointment updated: 7",
                "Appointment cancelled: 8"
        };
        for (String msg : filteredMessages) {
            jgit.commitJournalEntryWithHash(clientUuid.toString(), "Content", msg, "Normal Author");
        }
        List<Map<String, String>> thHist = jgit.getCommitHistory(clientUuid.toString(), true, journalService::getJournalContent);
        List<Map<String, String>> thHistCached = jgit.getCommitHistory(clientUuid.toString(), true, null);
        Assertions.assertEquals(thHist, thHistCached);

        // Also test getCommitHistory with null contentFilter when uncached
        Map<String, List<Map<String, String>>> hCache = (Map<String, List<Map<String, String>>>) ReflectionTestUtils.getField(jgit, "historyCache");
        hCache.clear();
        jgit.getCommitHistory(clientUuid.toString(), false, null);

        // Date and branch filtering in getCommitHistory
        Assertions.assertEquals(thHist, jgit.getCommitHistory(clientUuid.toString(), true, journalService::getJournalContent, null, null, null, null));
        Assertions.assertTrue(jgit.getCommitHistory(clientUuid.toString(), true, journalService::getJournalContent, LocalDate.now().plusDays(5), null, null, null).isEmpty());
        Assertions.assertTrue(jgit.getCommitHistory(clientUuid.toString(), true, journalService::getJournalContent, null, LocalDate.now().minusDays(5), null, null).isEmpty());
        Assertions.assertFalse(jgit.getCommitHistory(clientUuid.toString(), true, journalService::getJournalContent, LocalDate.now().minusDays(2), null, null, null).isEmpty());
        Assertions.assertFalse(jgit.getCommitHistory(clientUuid.toString(), true, journalService::getJournalContent, null, LocalDate.now().plusDays(2), null, null).isEmpty());

        hCache.put("custom-patient:false", List.of(
                new HashMap<>(),
                Map.of("timestamp", "not-a-timestamp")
        ));
        Assertions.assertEquals(2, jgit.getCommitHistory("custom-patient", false, journalService::getJournalContent, LocalDate.now(), LocalDate.now(), null, null).size());

        // extractBranchFromMessage & multi-branch commit history
        Assertions.assertEquals("main", JournalGitService.extractBranchFromMessage(null));
        Assertions.assertEquals("main", JournalGitService.extractBranchFromMessage("Plain message"));
        Assertions.assertEquals("main", JournalGitService.extractBranchFromMessage("[Branch: no closing bracket"));
        Assertions.assertEquals("main", JournalGitService.extractBranchFromMessage("[Branch:] empty"));
        Assertions.assertEquals("main", JournalGitService.extractBranchFromMessage("[Branch:   ] blank"));
        Assertions.assertEquals("trauma", JournalGitService.extractBranchFromMessage("[Branch: trauma] Commit msg"));

        Path tempMultiBranchRepo = Files.createTempDirectory("jgit-multibranch");
        JournalGitService mbGit = new JournalGitService();
        ReflectionTestUtils.setField(mbGit, "repoPath", tempMultiBranchRepo.toString());
        mbGit.init();
        mbGit.commitJournalEntryWithHash(clientUuid.toString(), "Entry 1", "[Branch: already-tagged] Msg", null, null);
        mbGit.commitJournalEntryWithHash(clientUuid.toString(), "Entry 2", null, "Author", "   ");
        mbGit.commitJournalEntryWithHash(clientUuid.toString(), "Entry 3", "Untagged", "Author", "branch-2");
        List<Map<String, String>> mbFiltered = mbGit.getCommitHistory(
                clientUuid.toString(), true, journalService::getJournalContent, null, null, null, List.of("already-tagged")
        );
        Assertions.assertEquals(1, mbFiltered.size());
        List<Map<String, String>> mbEmptyList = mbGit.getCommitHistory(
                clientUuid.toString(), true, journalService::getJournalContent, null, null, null, List.of()
        );
        Assertions.assertEquals(3, mbEmptyList.size());
        mbGit.close();

        // readBlobAtCommit when path not found in commit
        var readBlobMethod = JournalGitService.class.getDeclaredMethod(
                "readBlobAtCommit",
                org.eclipse.jgit.lib.Repository.class,
                org.eclipse.jgit.revwalk.RevCommit.class,
                String.class
        );
        readBlobMethod.setAccessible(true);
        org.eclipse.jgit.lib.Repository repo = jgit.getGit().getRepository();
        org.eclipse.jgit.revwalk.RevCommit headCommit = jgit.getGit().log().setMaxCount(1).call().iterator().next();
        Assertions.assertEquals("", readBlobMethod.invoke(jgit, repo, headCommit, "missing-file.md"));

        jgit.close();
        ReflectionTestUtils.setField(jgit, "git", null);
        jgit.close();
        // getCommitHistory catch block when git is null
        hCache.clear();
        Assertions.assertTrue(jgit.getCommitHistory(clientUuid.toString(), true, null).isEmpty());
        Assertions.assertThrows(RuntimeException.class, () -> jgit.commitJournalEntryWithHash(clientUuid.toString(), "x", "y", "z"));

        // init() and readJournalEntry() IOException branches when repoPath is a file
        Path fileInsteadOfDir = Files.createTempFile("not-a-dir", ".txt");
        JournalGitService brokenGit = new JournalGitService();
        ReflectionTestUtils.setField(brokenGit, "repoPath", fileInsteadOfDir.toString());
        Assertions.assertThrows(RuntimeException.class, brokenGit::init);

        // readJournalEntry IOException when patientId.md is a directory
        Path dirWithMdDir = Files.createTempDirectory("dir-with-md");
        Files.createDirectories(dirWithMdDir.resolve("bad-patient.md"));
        JournalGitService badReadGit = new JournalGitService();
        ReflectionTestUtils.setField(badReadGit, "repoPath", dirWithMdDir.toString());
        Assertions.assertThrows(RuntimeException.class, () -> badReadGit.readJournalEntry("bad-patient"));

        // 4. JournalService with HandshakePort and AppointmentPort
        Path hsRepoDir = Files.createTempDirectory("jgit-hs-test");
        JournalGitService okGit = new JournalGitService();
        ReflectionTestUtils.setField(okGit, "repoPath", hsRepoDir.toString());
        okGit.init();

        JournalEntryRepository mockJeRepo = Mockito.mock(JournalEntryRepository.class);
        TherapistClinicalNoteRepository mockNoteRepo = Mockito.mock(TherapistClinicalNoteRepository.class);
        AppointmentPort mockAptPort = Mockito.mock(AppointmentPort.class);
        HandshakePort mockHsPort = Mockito.mock(HandshakePort.class);
        PlatformCacheService customCache = new PlatformCacheService();

        Mockito.when(mockJeRepo.save(any(JournalEntry.class))).thenAnswer(i -> {
            JournalEntry e = i.getArgument(0);
            if (e.getId() == null) e.setId(UUID.randomUUID());
            return e;
        });
        Mockito.when(mockNoteRepo.save(any(TherapistClinicalNote.class))).thenAnswer(i -> {
            TherapistClinicalNote n = i.getArgument(0);
            if (n.getId() == null) n.setId(UUID.randomUUID());
            return n;
        });

        UUID aptId = UUID.randomUUID();
        Appointment linkedApt = new Appointment(clientUuid, therapistUuid, LocalDateTime.now(), LocalDateTime.now().plusHours(1), "Samtale", "SCHEDULED");
        linkedApt.setId(aptId);
        Mockito.when(mockAptPort.findAppointmentById(aptId)).thenReturn(Optional.of(linkedApt));
        Mockito.doAnswer(inv -> {
            linkedApt.setJournalEntryId(inv.getArgument(1));
            return null;
        }).when(mockAptPort).linkJournalEntry(Mockito.eq(aptId), any());

        JournalService fullJs = new JournalService(mockJeRepo, okGit, mockNoteRepo, customCache, mockAptPort, mockHsPort);
        JournalController fullCtrl = new JournalController(fullJs);

        JournalEntryDto aptEntryDto = new JournalEntryDto(
                null, "Session Noter", "Gode refleksioner", 7, true, null, clientUuid, aptId, "main"
        );
        fullCtrl.createJournalEntry(aptEntryDto, null);
        Assertions.assertNotNull(linkedApt.getJournalEntryId());

        Jwt tJwt = Jwt.withTokenValue("t-tok")
                .header("alg", "none")
                .claim("sub", therapistUuid.toString())
                .claim("realm_access", Map.of("roles", List.of("ROLE_THERAPIST")))
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(3600))
                .build();
        Authentication tAuth = Mockito.mock(Authentication.class);
        Mockito.when(tAuth.getPrincipal()).thenReturn(tJwt);

        Map<String, Object> noteWithApt = new HashMap<>();
        noteWithApt.put("clientUuid", clientUuid.toString());
        noteWithApt.put("therapistNote", "Klinisk opfølgning");
        noteWithApt.put("appointmentId", aptId.toString());
        fullCtrl.createTherapistNote(noteWithApt, tAuth);

        ClientTherapistHandshake hs = new ClientTherapistHandshake(clientUuid, "Anders", therapistUuid, "Dr. Jensen", "Psykologhuset", "ACTIVE");
        hs.setId(UUID.randomUUID());
        hs.setValidFrom(LocalDate.now().minusDays(5));
        hs.setValidTo(LocalDate.now().plusDays(5));
        hs.setHistoryStartDate(LocalDate.now().minusDays(2));
        hs.setHistoryEndDate(LocalDate.now().plusDays(2));
        Mockito.when(mockHsPort.findByClientUuidAndTherapistUuid(clientUuid, therapistUuid)).thenReturn(Optional.of(hs));

        JournalEntry inRangeEntry = new JournalEntry("I dag", "Indhold", 7, LocalDateTime.now(), clientUuid);
        JournalEntry oldEntry = new JournalEntry("Gammel", "Indhold", 5, LocalDateTime.now().minusDays(10), clientUuid);
        JournalEntry futureEntry = new JournalEntry("Fremtid", "Indhold", 8, LocalDateTime.now().plusDays(10), clientUuid);
        Mockito.when(mockJeRepo.findByClientUuidOrderByTimestampDesc(clientUuid)).thenReturn(List.of(inRangeEntry, oldEntry, futureEntry));

        Map<String, Object> filteredBundle = fullCtrl.getClientJournalBundle(clientUuid, therapistUuid, tAuth).getBody();
        Assertions.assertEquals(1, ((List<?>) filteredBundle.get("entries")).size());
        Assertions.assertFalse(fullCtrl.getJournalCommitHistory(clientUuid, therapistUuid, tAuth).getBody().isEmpty());

        hs.setStatus("REVOKED");
        Assertions.assertTrue(((List<?>) fullCtrl.getClientJournalBundle(clientUuid, therapistUuid, tAuth).getBody().get("entries")).isEmpty());
        Assertions.assertTrue(fullCtrl.getJournalCommitHistory(clientUuid, therapistUuid, tAuth).getBody().isEmpty());

        // Failing git and edge cases in JournalService
        JournalGitService failingGit = Mockito.mock(JournalGitService.class);
        Mockito.when(failingGit.commitJournalEntryWithHash(any(), any(), any(), any(), any())).thenThrow(new RuntimeException("Git failure"));
        JournalService jsWithFailingGit = new JournalService(mockJeRepo, failingGit, mockNoteRepo, customCache, mockAptPort, mockHsPort);

        JournalEntryDto privateDto = new JournalEntryDto(
                null, null, "Already has Refs-Appointment", null, false, null, null, UUID.randomUUID(), "main"
        );
        Authentication nonJwtAuth = Mockito.mock(Authentication.class);
        Mockito.when(nonJwtAuth.getPrincipal()).thenReturn("stringPrincipal");
        jsWithFailingGit.createJournalEntry(privateDto, nonJwtAuth);

        JournalEntry entryNoClient = new JournalEntry();
        UUID entryNoClientId = UUID.randomUUID();
        Mockito.when(mockJeRepo.findById(entryNoClientId)).thenReturn(Optional.of(entryNoClient));
        Map<String, Object> sharingPayload = new HashMap<>();
        sharingPayload.put("sharedWithTherapists", null);
        List<Object> listWithNullAndBlank = new ArrayList<>();
        listWithNullAndBlank.add(null);
        listWithNullAndBlank.add("   ");
        sharingPayload.put("sharedTherapistUuids", listWithNullAndBlank);
        jsWithFailingGit.updateJournalEntrySharing(entryNoClientId, sharingPayload);
        jsWithFailingGit.updateJournalEntrySharing(entryNoClientId, Map.of());
        jsWithFailingGit.updateJournalEntrySharing(entryNoClientId, Map.of("gitBranch", "branch-custom"));
        Map<String, Object> nullBranchMap = new HashMap<>();
        nullBranchMap.put("gitBranch", null);
        jsWithFailingGit.updateJournalEntrySharing(entryNoClientId, nullBranchMap);

        Assertions.assertNull(jsWithFailingGit.createTherapistNote(Map.of("clientUuid", "", "therapistNote", "note"), tAuth));
        Assertions.assertNull(jsWithFailingGit.createTherapistNote(Map.of("clientUuid", clientUuid.toString(), "therapistNote", "   ", "appointmentId", "  "), tAuth));
        Assertions.assertNotNull(jsWithFailingGit.createTherapistNote(Map.of("clientUuid", clientUuid.toString(), "therapistNote", "note", "appointmentId", "bad-uuid"), tAuth));

        JournalService jsNoNoteRepo = new JournalService(mockJeRepo, null, null, null, null, null);
        Assertions.assertNull(jsNoNoteRepo.createTherapistNote(Map.of("clientUuid", clientUuid.toString(), "therapistNote", "note Refs-Appointment"), tAuth));

        TherapistClinicalNoteRepository throwingNoteRepo = Mockito.mock(TherapistClinicalNoteRepository.class);
        Mockito.when(throwingNoteRepo.save(any())).thenThrow(new RuntimeException("DB fail"));
        JournalService jsThrowingNote = new JournalService(mockJeRepo, null, throwingNoteRepo, customCache, null, null);
        Assertions.assertNull(jsThrowingNote.createTherapistNote(
                Map.of("clientUuid", clientUuid.toString(), "therapistNote", "note", "appointmentId", UUID.randomUUID().toString()),
                tAuth
        ));

        TherapistClinicalNoteRepository okNoteRepo = Mockito.mock(TherapistClinicalNoteRepository.class);
        Mockito.when(okNoteRepo.save(any())).thenAnswer(i -> i.getArgument(0));
        JournalService jsNoteNoAptRepo = new JournalService(mockJeRepo, null, okNoteRepo, customCache, null, null);
        Assertions.assertNotNull(jsNoteNoAptRepo.createTherapistNote(
                Map.of("clientUuid", clientUuid.toString(), "therapistNote", "note Refs-Appointment", "appointmentId", UUID.randomUUID().toString()),
                tAuth
        ));

        JournalEntry nullTsEntry = new JournalEntry("Null TS", "Body", 5, LocalDateTime.now(), clientUuid);
        ReflectionTestUtils.setField(nullTsEntry, "timestamp", null);
        Mockito.when(mockJeRepo.findByClientUuidOrderByTimestampDesc(clientUuid)).thenReturn(List.of(nullTsEntry));
        jsNoNoteRepo.getClientJournalBundle(clientUuid, clientUuid, tAuth);
        jsNoNoteRepo.getClientJournalBundle(clientUuid, null, nonJwtAuth);

        ClientTherapistHandshake hsStartOnly = new ClientTherapistHandshake(clientUuid, "C", therapistUuid, "T", "O", "ACTIVE");
        hsStartOnly.setHistoryStartDate(LocalDate.now().minusDays(1));
        Mockito.when(mockHsPort.findByClientUuidAndTherapistUuid(clientUuid, therapistUuid)).thenReturn(Optional.of(hsStartOnly));
        Map<String, Object> bundleStartOnly = jsWithFailingGit.getClientJournalBundle(clientUuid, therapistUuid, null);
        Assertions.assertNotNull(bundleStartOnly.get("historyStartDate"));
        Assertions.assertNull(bundleStartOnly.get("historyEndDate"));

        ClientTherapistHandshake hsEndOnly = new ClientTherapistHandshake(clientUuid, "C", therapistUuid, "T", "O", "ACTIVE");
        hsEndOnly.setHistoryEndDate(LocalDate.now().plusDays(1));
        Mockito.when(mockHsPort.findByClientUuidAndTherapistUuid(clientUuid, therapistUuid)).thenReturn(Optional.of(hsEndOnly));
        Map<String, Object> bundleEndOnly = jsWithFailingGit.getClientJournalBundle(clientUuid, therapistUuid, null);
        Assertions.assertNull(bundleEndOnly.get("historyStartDate"));
        Assertions.assertNotNull(bundleEndOnly.get("historyEndDate"));

        Authentication roleAuth = Mockito.mock(Authentication.class);
        Mockito.doReturn(List.of(new SimpleGrantedAuthority("ROLE_ROLE_THERAPIST"))).when(roleAuth).getAuthorities();
        Assertions.assertTrue(journalService.hasTherapistRole(roleAuth));

        Jwt invalidSubJwt = Jwt.withTokenValue("tok")
                .header("alg", "none")
                .claim("sub", "not-a-uuid")
                .claim("realm_access", Map.of("roles", "not-a-list"))
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(3600))
                .build();
        Authentication invalidSubAuth = Mockito.mock(Authentication.class);
        Mockito.when(invalidSubAuth.getPrincipal()).thenReturn(invalidSubJwt);
        Mockito.when(invalidSubAuth.getAuthorities()).thenReturn(null);
        Assertions.assertFalse(journalService.hasTherapistRole(invalidSubAuth));

        JournalEntryDto dtoNullSub = new JournalEntryDto(
                null, "Title", null, 7, true, null, null, UUID.randomUUID(), "main"
        );
        jsWithFailingGit.createJournalEntry(dtoNullSub, invalidSubAuth);

        JournalEntryRepository nullIdReturningJeRepo = Mockito.mock(JournalEntryRepository.class);
        Mockito.when(nullIdReturningJeRepo.save(any())).thenReturn(new JournalEntry());
        JournalService jsNullIdSave = new JournalService(
                nullIdReturningJeRepo, null, null, customCache, mockAptPort, null
        );
        jsNullIdSave.createJournalEntry(dtoNullSub, null);

        Jwt noSubJwt = Jwt.withTokenValue("tok")
                .header("alg", "none")
                .claim("iss", "test")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(3600))
                .build();
        Authentication noSubAuth = Mockito.mock(Authentication.class);
        Mockito.when(noSubAuth.getPrincipal()).thenReturn(noSubJwt);
        Assertions.assertNull(journalService.extractCallerUuid(noSubAuth));

        Mockito.when(mockHsPort.findByClientUuidAndTherapistUuid(clientUuid, therapistUuid)).thenReturn(Optional.empty());
        Assertions.assertTrue(((List<?>) jsWithFailingGit.getClientJournalBundle(clientUuid, therapistUuid, null).get("entries")).isEmpty());
        Assertions.assertTrue(jsWithFailingGit.getJournalCommitHistory(clientUuid, therapistUuid, null).isEmpty());

        JournalService jsWithOkGit = new JournalService(mockJeRepo, okGit, okNoteRepo, customCache, null, null);
        jsWithOkGit.getJournalCommitHistory(clientUuid, clientUuid, tAuth);
        jsWithOkGit.getJournalCommitHistory(clientUuid, null, roleAuth);
        jsWithOkGit.getJournalCommitHistory(clientUuid, null, nonJwtAuth);

        JournalService jsNoAptRepo = new JournalService(mockJeRepo, null, null, customCache, null, mockHsPort);
        JournalEntryDto dtoWithApt = new JournalEntryDto(
                null, "T", "C", 7, true, null, clientUuid, UUID.randomUUID(), "main"
        );
        jsNoAptRepo.createJournalEntry(dtoWithApt, null);

        Assertions.assertThrows(DomainException.class, () -> jsNoAptRepo.createTherapistNote(Map.of(), null));
        Assertions.assertThrows(DomainException.class, () -> jsNoAptRepo.createTherapistNote(Map.of(), nonJwtAuth));

        ClientTherapistHandshake hsBothDates = new ClientTherapistHandshake(clientUuid, "C", therapistUuid, "T", "O", "ACTIVE");
        hsBothDates.setHistoryStartDate(LocalDate.now().minusDays(2));
        hsBothDates.setHistoryEndDate(LocalDate.now().plusDays(2));
        hsBothDates.setAllowedBranches("main,branch-open");
        hsBothDates.setCustomRestrictions(null);
        hsBothDates.setBranchRestrictions("main:NO_MOOD_SCORES;branch-open:READ_ONLY_TIMELINE");
        Mockito.when(mockHsPort.findByClientUuidAndTherapistUuid(clientUuid, therapistUuid)).thenReturn(Optional.of(hsBothDates));

        JournalEntry eBefore = new JournalEntry("Before", "B", 5, LocalDateTime.now().minusDays(5), clientUuid);
        JournalEntry eIn = new JournalEntry("In", "I", 5, LocalDateTime.now(), clientUuid);
        JournalEntry eAfter = new JournalEntry("After", "A", 5, LocalDateTime.now().plusDays(5), clientUuid);
        JournalEntry openBranchEntry = new JournalEntry("Open", "Content", 9, LocalDateTime.now(), clientUuid);
        openBranchEntry.setSharedWithTherapists(true);
        openBranchEntry.setGitBranch("branch-open");
        Mockito.when(mockJeRepo.findByClientUuidOrderByTimestampDesc(clientUuid)).thenReturn(List.of(eBefore, eIn, eAfter, openBranchEntry));

        jsNoAptRepo.getClientJournalBundle(clientUuid, null, null);
        jsNoAptRepo.getClientJournalBundle(clientUuid, null, roleAuth);
        jsNoAptRepo.getClientJournalBundle(clientUuid, therapistUuid, tAuth);

        hsBothDates.setHistoryEndDate(null);
        jsNoAptRepo.getClientJournalBundle(clientUuid, therapistUuid, tAuth);
        hsBothDates.setHistoryStartDate(null);
        hsBothDates.setHistoryEndDate(LocalDate.now().plusDays(2));
        jsNoAptRepo.getClientJournalBundle(clientUuid, therapistUuid, tAuth);

        TherapistClinicalNote cnSample = new TherapistClinicalNote(clientUuid, therapistUuid, "Dr. T", "Org", UUID.randomUUID(), UUID.randomUUID(), "Md", "h1");
        cnSample.setId(UUID.randomUUID());
        TherapistClinicalNoteDto cnDto = TherapistClinicalNoteDto.fromEntity(cnSample);
        Assertions.assertEquals(cnSample.getId(), cnDto.id());

        TherapistNote tnSample = new TherapistNote(clientUuid, therapistUuid, "Note text");
        tnSample.setId(UUID.randomUUID());
        TherapistNoteDto tnDto = TherapistNoteDto.fromEntity(tnSample);
        Assertions.assertEquals(tnSample.getId(), tnDto.id());

        okGit.close();

        // Remaining JaCoCo branches:
        // 1. commitJournalEntryWithHash IOException catch block (L593-595)
        Path ioRepo = Files.createTempDirectory("jgit-io-fail");
        JournalGitService ioGitSvc = new JournalGitService();
        ReflectionTestUtils.setField(ioGitSvc, "repoPath", ioRepo.toString());
        ioGitSvc.init();
        Files.createDirectories(ioRepo.resolve("bad-patient.md"));
        Assertions.assertThrows(RuntimeException.class,
                () -> ioGitSvc.commitJournalEntryWithHash("bad-patient", "c", "m", "a", "main"));
        ioGitSvc.close();

        // 2. getCommitHistory null fullMessage, null authorIdent, plain commit msg, unclosed [Branch: (L645, L647, L650, L726)
        Path spyRepo = Files.createTempDirectory("jgit-spy-repo");
        JournalGitService realGitSvc = new JournalGitService();
        ReflectionTestUtils.setField(realGitSvc, "repoPath", spyRepo.toString());
        realGitSvc.init();
        String patientSpyId = "patient-spy";
        realGitSvc.commitJournalEntry(patientSpyId, "Snapshot body", "[Branch: main] Initial commit");
        org.eclipse.jgit.api.Git realGit = realGitSvc.getGit();
        org.eclipse.jgit.revwalk.RevCommit realCommit = realGit.log().addPath(patientSpyId + ".md").call().iterator().next();

        org.eclipse.jgit.revwalk.RevCommit cNullMsg = Mockito.spy(realCommit);
        Mockito.doReturn(null).when(cNullMsg).getFullMessage();

        org.eclipse.jgit.revwalk.RevCommit cNullAuthorBooking = Mockito.spy(realCommit);
        Mockito.doReturn(null).when(cNullAuthorBooking).getAuthorIdent();
        Mockito.doReturn("aftale oprettet").when(cNullAuthorBooking).getFullMessage();

        org.eclipse.jgit.revwalk.RevCommit cPlainMsg = Mockito.spy(realCommit);
        Mockito.doReturn("plain commit message").when(cPlainMsg).getFullMessage();

        org.eclipse.jgit.revwalk.RevCommit cUnclosed = Mockito.spy(realCommit);
        Mockito.doReturn("[Branch:unclosed").when(cUnclosed).getFullMessage();

        JournalGitService spyGitSvc = new JournalGitService();
        ReflectionTestUtils.setField(spyGitSvc, "repoPath", spyRepo.toString());
        org.eclipse.jgit.api.Git mockGit = Mockito.mock(org.eclipse.jgit.api.Git.class);
        org.eclipse.jgit.api.LogCommand mockLog = Mockito.mock(org.eclipse.jgit.api.LogCommand.class);
        Mockito.when(mockGit.log()).thenReturn(mockLog);
        Mockito.when(mockLog.addPath(patientSpyId + ".md")).thenReturn(mockLog);
        Mockito.when(mockLog.call()).thenReturn(List.of(cNullMsg, cNullAuthorBooking, cPlainMsg, cUnclosed));
        Mockito.when(mockGit.getRepository()).thenReturn(realGit.getRepository());
        ReflectionTestUtils.setField(spyGitSvc, "git", mockGit);

        List<Map<String, String>> spyHistory = spyGitSvc.getCommitHistory(patientSpyId, true, null);
        Assertions.assertEquals(3, spyHistory.size());
        realGitSvc.close();

        // 3. createJournalEntry: save() returning null (L934) & saved with appointmentId != null when appointmentRepository == null (L934)
        JournalEntryRepository nullSaveRepo = Mockito.mock(JournalEntryRepository.class);
        Mockito.when(nullSaveRepo.save(any(JournalEntry.class))).thenReturn(null);
        JournalService nullSaveSvc = new JournalService(
                nullSaveRepo, null, null, Mockito.mock(PlatformCacheService.class), null, null
        );
        Assertions.assertNull(nullSaveSvc.createJournalEntry(
                new JournalEntryDto(null, "T", "C", 7, true, null, clientUuid, null, "main"), null));

        JournalService bareJeSvc = new JournalService(mockJeRepo);
        Assertions.assertNotNull(bareJeSvc.createJournalEntry(
                new JournalEntryDto(null, "T2", "C2", 7, true, null, clientUuid, UUID.randomUUID(), "main"), null));

        // 4. getClientJournalBundle: therapist viewing client with handshakeRepository == null -> activeHandshake == null & sharedWithTherapists = true (L1076)
        JournalService noHsTherapistSvc = new JournalService(mockJeRepo, null, null, customCache, null, null);
        Map<String, Object> bundleNoHs = noHsTherapistSvc.getClientJournalBundle(clientUuid, therapistUuid, tAuth);
        Assertions.assertFalse(((List<?>) bundleNoHs.get("entries")).isEmpty());

        // 5. getClientJournalBundle: shared entry on a branch disallowed by active handshake is filtered out (L1076 false arm)
        ClientTherapistHandshake mainOnlyHs = new ClientTherapistHandshake(clientUuid, "C", therapistUuid, "T", "O", "ACTIVE");
        mainOnlyHs.setAllowedBranches("main");
        HandshakePort mainOnlyPort = Mockito.mock(HandshakePort.class);
        Mockito.when(mainOnlyPort.findByClientUuidAndTherapistUuid(clientUuid, therapistUuid)).thenReturn(Optional.of(mainOnlyHs));
        JournalEntry blockedBranchEntry = new JournalEntry("Blocked", "Hidden branch", 6, LocalDateTime.now(), clientUuid);
        blockedBranchEntry.setSharedWithTherapists(true);
        blockedBranchEntry.setGitBranch("trauma");
        JournalEntryRepository blockedRepo = Mockito.mock(JournalEntryRepository.class);
        Mockito.when(blockedRepo.findByClientUuidOrderByTimestampDesc(clientUuid)).thenReturn(List.of(blockedBranchEntry));
        JournalService mainOnlySvc = new JournalService(blockedRepo, null, null, customCache, null, mainOnlyPort);
        Map<String, Object> blockedBranchBundle = mainOnlySvc.getClientJournalBundle(clientUuid, therapistUuid, tAuth);
        Assertions.assertTrue(((List<?>) blockedBranchBundle.get("entries")).isEmpty());
    }
}
