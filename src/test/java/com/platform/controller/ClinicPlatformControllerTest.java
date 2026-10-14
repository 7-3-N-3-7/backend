package com.platform.controller;

import com.platform.crm.model.JournalEntry;
import com.platform.crm.model.TherapistClinicalNote;
import com.platform.crm.repository.JournalEntryRepository;
import com.platform.crm.repository.TherapistClinicalNoteRepository;
import com.platform.dto.JournalEntryDto;
import com.platform.entity.*;
import com.platform.repository.*;
import com.platform.service.JournalGitService;
import com.platform.service.JournalService;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.*;

import static org.mockito.ArgumentMatchers.any;

public class ClinicPlatformControllerTest {

    private ClientProfileRepository clientProfileRepository;
    private OrganisationRepository organisationRepository;
    private ProviderProfileRepository providerProfileRepository;
    private ServiceRepository serviceRepository;
    private HandshakeRepository handshakeRepository;
    private ClinicPlatformController controller;

    @BeforeEach
    public void setUp() {
        clientProfileRepository = Mockito.mock(ClientProfileRepository.class);
        organisationRepository = Mockito.mock(OrganisationRepository.class);
        providerProfileRepository = Mockito.mock(ProviderProfileRepository.class);
        serviceRepository = Mockito.mock(ServiceRepository.class);
        handshakeRepository = Mockito.mock(HandshakeRepository.class);

        Mockito.when(clientProfileRepository.save(any(ClientProfile.class))).thenAnswer(i -> i.getArgument(0));
        Mockito.when(organisationRepository.save(any(Organisation.class))).thenAnswer(i -> {
            Organisation o = i.getArgument(0);
            o.setId(UUID.randomUUID());
            return o;
        });
        Mockito.when(providerProfileRepository.save(any(ProviderProfile.class))).thenAnswer(i -> {
            ProviderProfile p = i.getArgument(0);
            p.setId(UUID.randomUUID());
            return p;
        });
        Mockito.when(serviceRepository.save(any(Service.class))).thenAnswer(i -> {
            Service s = i.getArgument(0);
            s.setId(UUID.randomUUID());
            return s;
        });
        Mockito.when(handshakeRepository.save(any(ClientTherapistHandshake.class))).thenAnswer(i -> {
            ClientTherapistHandshake h = i.getArgument(0);
            if (h.getId() == null) h.setId(UUID.randomUUID());
            return h;
        });

        controller = new ClinicPlatformController(
                clientProfileRepository,
                organisationRepository,
                providerProfileRepository,
                serviceRepository,
                handshakeRepository
        );
    }

    @Test
    public void testClientProfileCreateAndUpdateAndGet() {
        UUID userId = UUID.randomUUID();
        ClientProfile profile = new ClientProfile(userId, "Anna Jensen", "1992-05-14", "Nørrebrogade 10", "2200", "+4512345678", "anna@example.com");
        profile.setId(UUID.randomUUID());

        Mockito.when(clientProfileRepository.findByUserId(userId)).thenReturn(Optional.empty());
        ResponseEntity<ClientProfile> created = controller.savePublicClientProfile(profile);
        Assertions.assertEquals(HttpStatus.CREATED, created.getStatusCode());
        Assertions.assertEquals("Anna Jensen", created.getBody().getFullName());

        // Update existing
        Mockito.when(clientProfileRepository.findByUserId(userId)).thenReturn(Optional.of(profile));
        ClientProfile updatedReq = new ClientProfile(userId, "Anna K. Jensen", "1992-05-14", "Østerbrogade 20", "2100", "+4587654321", "anna2@example.com");
        ResponseEntity<ClientProfile> updated = controller.savePublicClientProfile(updatedReq);
        Assertions.assertEquals(HttpStatus.OK, updated.getStatusCode());
        Assertions.assertEquals("Anna K. Jensen", updated.getBody().getFullName());
        Assertions.assertEquals("2100", updated.getBody().getPostalCode());

        // Null userId auto-generates
        ClientProfile nullUserReq = new ClientProfile();
        nullUserReq.setFullName("Auto User");
        ResponseEntity<ClientProfile> autoCreated = controller.savePublicClientProfile(nullUserReq);
        Assertions.assertNotNull(autoCreated.getBody().getUserId());

        // Get found & not found
        Assertions.assertEquals(HttpStatus.OK, controller.getClientProfile(userId).getStatusCode());
        Assertions.assertEquals(HttpStatus.NOT_FOUND, controller.getClientProfile(UUID.randomUUID()).getStatusCode());
    }

    @Test
    public void testTherapistOnboardingAndOrganisations() {
        Mockito.when(organisationRepository.findAll()).thenReturn(List.of(new Organisation("12345678", "Nordic Mind Clinic")));
        Assertions.assertEquals(1, controller.getOrganisations().getBody().size());

        // Missing CVR throws 400
        Assertions.assertThrows(ResponseStatusException.class, () -> controller.onboardTherapist(Map.of("cvrNumber", "   ")));

        // Missing org name for new CVR throws 400
        Mockito.when(organisationRepository.findByCvrNumber("99999999")).thenReturn(Optional.empty());
        Assertions.assertThrows(ResponseStatusException.class, () -> controller.onboardTherapist(Map.of("cvrNumber", "99999999")));

        // Create new Organisation & ProviderProfile
        UUID userUuid = UUID.randomUUID();
        Map<String, String> payload = new HashMap<>();
        payload.put("cvrNumber", "87654321");
        payload.put("organisationName", "Københavns Psykoterapi ApS");
        payload.put("userUuid", userUuid.toString());
        payload.put("displayName", "Dr. Maja Holm");
        payload.put("biography", "Specialist i traumer");
        payload.put("workingHoursJson", "{\"mon_fri\":\"08:00-16:00\"}");

        Mockito.when(organisationRepository.findByCvrNumber("87654321")).thenReturn(Optional.empty());
        Mockito.when(providerProfileRepository.findByUserUuid(userUuid)).thenReturn(Optional.empty());

        ResponseEntity<ProviderProfile> onboarded = controller.onboardTherapist(payload);
        Assertions.assertEquals(HttpStatus.OK, onboarded.getStatusCode());
        Assertions.assertEquals("Dr. Maja Holm", onboarded.getBody().getDisplayName());
        Assertions.assertEquals("87654321", onboarded.getBody().getOrganisation().getCvrNumber());

        // Join existing Organisation
        Organisation existingOrg = new Organisation("87654321", "Københavns Psykoterapi ApS");
        existingOrg.setId(UUID.randomUUID());
        Mockito.when(organisationRepository.findByCvrNumber("87654321")).thenReturn(Optional.of(existingOrg));
        ResponseEntity<ProviderProfile> joined = controller.onboardTherapist(Map.of("cvrNumber", "87654321"));
        Assertions.assertEquals(HttpStatus.OK, joined.getStatusCode());
    }

    @Test
    public void testServiceCatalogCreateAndToggle() {
        UUID providerId = UUID.randomUUID();
        UUID orgId = UUID.randomUUID();
        ProviderProfile provider = new ProviderProfile();
        provider.setId(providerId);
        Organisation org = new Organisation("12345678", "Clinic");
        org.setId(orgId);

        Mockito.when(providerProfileRepository.findById(providerId)).thenReturn(Optional.of(provider));
        Mockito.when(organisationRepository.findById(orgId)).thenReturn(Optional.of(org));

        Map<String, Object> req = new HashMap<>();
        req.put("name", "Traumeterapi (PCL-5 Opfølgning)");
        req.put("description", "Individuel session");
        req.put("durationMinutes", 60);
        req.put("price", 950.0);
        req.put("location", "Klinik / Online");
        req.put("providerProfileId", providerId.toString());
        req.put("organisationId", orgId.toString());

        ResponseEntity<Service> created = controller.createService(req);
        Assertions.assertEquals(HttpStatus.CREATED, created.getStatusCode());
        Service svc = created.getBody();
        Assertions.assertTrue(svc.getIsActive());
        Assertions.assertEquals(60, svc.getDurationMinutes());

        Mockito.when(serviceRepository.findById(svc.getId())).thenReturn(Optional.of(svc));
        ResponseEntity<Service> toggled = controller.toggleServiceActive(svc.getId());
        Assertions.assertFalse(toggled.getBody().getIsActive());

        Mockito.when(serviceRepository.findAll()).thenReturn(List.of(svc));
        Assertions.assertEquals(1, controller.getAllServices().getBody().size());

        Assertions.assertThrows(ResponseStatusException.class, () -> controller.toggleServiceActive(UUID.randomUUID()));
    }

    @Test
    public void testHandshakesLifecycle() {
        UUID clientUuid = UUID.randomUUID();
        UUID therapistUuid = UUID.randomUUID();

        ClientTherapistHandshake req = new ClientTherapistHandshake(
                clientUuid, "Anna Jensen", therapistUuid, "Dr. Maja Holm", "Nordic Mind", "ACTIVE"
        );
        Mockito.when(handshakeRepository.findByClientUuidAndTherapistUuid(clientUuid, therapistUuid)).thenReturn(Optional.empty());
        ResponseEntity<ClientTherapistHandshake> created = controller.createOrUpdateHandshake(req);
        Assertions.assertEquals(HttpStatus.CREATED, created.getStatusCode());
        Assertions.assertNotNull(created.getBody().getGrantedAt());

        // Existing handshake update
        UUID handshakeId = created.getBody().getId();
        Mockito.when(handshakeRepository.findByClientUuidAndTherapistUuid(clientUuid, therapistUuid)).thenReturn(Optional.of(created.getBody()));
        ResponseEntity<ClientTherapistHandshake> reActivated = controller.createOrUpdateHandshake(req);
        Assertions.assertEquals(HttpStatus.OK, reActivated.getStatusCode());

        // Revoke & Accept via PATCH
        Mockito.when(handshakeRepository.findById(handshakeId)).thenReturn(Optional.of(created.getBody()));
        ResponseEntity<ClientTherapistHandshake> revoked = controller.updateHandshakeStatus(handshakeId, Map.of("status", "REVOKED"));
        Assertions.assertEquals("REVOKED", revoked.getBody().getStatus());
        Assertions.assertNotNull(revoked.getBody().getRevokedAt());

        ResponseEntity<ClientTherapistHandshake> accepted = controller.updateHandshakeStatus(handshakeId, Map.of("status", "ACTIVE"));
        Assertions.assertEquals("ACTIVE", accepted.getBody().getStatus());
        Assertions.assertNull(accepted.getBody().getRevokedAt());

        Mockito.when(handshakeRepository.findByClientUuid(clientUuid)).thenReturn(List.of(created.getBody()));
        Mockito.when(handshakeRepository.findByTherapistUuid(therapistUuid)).thenReturn(List.of(created.getBody()));
        Assertions.assertEquals(1, controller.getClientHandshakes(clientUuid).getBody().size());
        Assertions.assertEquals(1, controller.getTherapistHandshakes(therapistUuid).getBody().size());

        Assertions.assertThrows(ResponseStatusException.class, () -> controller.updateHandshakeStatus(UUID.randomUUID(), Map.of("status", "REVOKED")));
    }

    @Test
    public void testJournalGitServiceAndControllerBundle() throws Exception {
        Path tempRepo = Files.createTempDirectory("jgit-test-repo");
        JournalGitService gitService = new JournalGitService();
        ReflectionTestUtils.setField(gitService, "repoPath", tempRepo.toString());
        gitService.init();

        JournalService journalService = new JournalService();
        JournalEntryRepository entryRepo = Mockito.mock(JournalEntryRepository.class);
        TherapistClinicalNoteRepository noteRepo = Mockito.mock(TherapistClinicalNoteRepository.class);
        Mockito.when(entryRepo.save(any(JournalEntry.class))).thenAnswer(i -> i.getArgument(0));
        Mockito.when(noteRepo.save(any(TherapistClinicalNote.class))).thenAnswer(i -> i.getArgument(0));

        JournalController journalController = new JournalController(entryRepo, gitService, noteRepo, journalService);

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

        JournalEntryDto dto = new JournalEntryDto();
        dto.setTitle("Uge 38 Tanker");
        dto.setContent("Jeg har sovet bedre i denne uge.");
        dto.setMoodRating(8);
        dto.setSharedWithTherapists(true);

        ResponseEntity<JournalEntry> savedEntry = journalController.createJournalEntry(dto, clientAuth);
        Assertions.assertEquals(HttpStatus.OK, savedEntry.getStatusCode());
        Assertions.assertNotNull(savedEntry.getBody().getGitCommitHash());

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
        Assertions.assertTrue(gitService.getCommitHistory("non-existent-id", true, journalService).isEmpty());
        Assertions.assertNotNull(gitService.getGit());
        gitService.close();
    }
}
