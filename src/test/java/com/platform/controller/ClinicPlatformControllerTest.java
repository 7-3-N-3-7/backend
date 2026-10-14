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

        // Join existing Organisation & upgrade authenticated client to ROLE_therapist
        Organisation existingOrg = new Organisation("87654321", "Københavns Psykoterapi ApS");
        existingOrg.setId(UUID.randomUUID());
        Mockito.when(organisationRepository.findByCvrNumber("87654321")).thenReturn(Optional.of(existingOrg));
        ResponseEntity<ProviderProfile> joined = controller.onboardTherapist(Map.of("cvrNumber", "87654321"));
        Assertions.assertEquals(HttpStatus.OK, joined.getStatusCode());

        // Test authenticated client onboarding via onboardTherapist(payload, auth)
        UUID upgradingClientUuid = UUID.randomUUID();
        Jwt clientJwt = Jwt.withTokenValue("client-tok")
                .header("alg", "none")
                .claim("sub", upgradingClientUuid.toString())
                .claim("realm_access", Map.of("roles", List.of("client")))
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(3600))
                .build();
        Authentication clientAuth = new org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken(
                clientJwt,
                List.of(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_client"))
        );

        ResponseEntity<ProviderProfile> upgraded = controller.onboardTherapist(Map.of("cvrNumber", "87654321"), clientAuth);
        Assertions.assertEquals(HttpStatus.OK, upgraded.getStatusCode());
        Assertions.assertEquals(upgradingClientUuid, upgraded.getBody().getUserUuid());
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

        // Test JournalController 2-arg constructor, null auth on createJournalEntry, 401/403 on createTherapistNote, blank payload, and timeline
        JournalController twoArgJc = new JournalController(entryRepo, gitService);
        Assertions.assertEquals(HttpStatus.OK, twoArgJc.createJournalEntry(dto, null).getStatusCode());
        Assertions.assertEquals(HttpStatus.OK, twoArgJc.createJournalEntry(dto, nonUuidClientAuth).getStatusCode());
        Assertions.assertThrows(ResponseStatusException.class, () -> twoArgJc.createTherapistNote(notePayload, null));
        Assertions.assertThrows(ResponseStatusException.class, () -> twoArgJc.createTherapistNote(notePayload, clientAuth));
        Assertions.assertEquals(HttpStatus.OK, twoArgJc.createTherapistNote(Map.of(), therapistAuth).getStatusCode());
        Assertions.assertEquals(HttpStatus.OK, twoArgJc.createTherapistNote(notePayload, therapistAuth).getStatusCode());
        Assertions.assertEquals(HttpStatus.OK, twoArgJc.getClientTimeline(clientUuid, therapistAuth).getStatusCode());
        Assertions.assertEquals(HttpStatus.OK, twoArgJc.getClientJournalBundle(clientUuid, null).getStatusCode());

        gitService.close();
    }

    @Test
    public void testClinicMeDirectoryAndOverviews() {
        AppointmentRepository appointmentRepository = Mockito.mock(AppointmentRepository.class);
        JournalEntryRepository journalEntryRepository = Mockito.mock(JournalEntryRepository.class);
        com.platform.crm.repository.PsychotraumatologyTestRepository pcl5Repository =
                Mockito.mock(com.platform.crm.repository.PsychotraumatologyTestRepository.class);

        ClinicPlatformController fullController = new ClinicPlatformController(
                clientProfileRepository,
                organisationRepository,
                providerProfileRepository,
                serviceRepository,
                handshakeRepository,
                appointmentRepository,
                journalEntryRepository,
                pcl5Repository
        );

        // 1. /clinic/me with null auth throws 401
        Assertions.assertThrows(ResponseStatusException.class, () -> fullController.getCurrentUserContext(null));

        // 2. /clinic/me with JWT containing therapist + admin roles and existing ClientProfile + ProviderProfile
        UUID userUuid = UUID.randomUUID();
        ClientProfile existingCp = new ClientProfile(userUuid, "Bo Jensen", "1984-02-24", "Bredgade 45", "1260", "+4540112233", "bjens@mail.com");
        ProviderProfile existingPp = new ProviderProfile();
        existingPp.setId(UUID.randomUUID());
        existingPp.setUserUuid(userUuid);
        existingPp.setDisplayName("Bo Jensen (Aut. Psykolog)");

        Mockito.when(clientProfileRepository.findByUserId(userUuid)).thenReturn(Optional.of(existingCp));
        Mockito.when(providerProfileRepository.findByUserUuid(userUuid)).thenReturn(Optional.of(existingPp));

        Jwt jwt = Jwt.withTokenValue("tok")
                .header("alg", "none")
                .claim("sub", userUuid.toString())
                .claim("preferred_username", "bjens")
                .claim("name", "Bo Jensen")
                .claim("email", "bjens@mail.com")
                .claim("realm_access", Map.of("roles", List.of("therapist", "admin")))
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(3600))
                .build();
        Authentication auth = Mockito.mock(Authentication.class);
        Mockito.when(auth.getPrincipal()).thenReturn(jwt);

        ResponseEntity<Map<String, Object>> meTherapist = fullController.getCurrentUserContext(auth);
        Assertions.assertEquals(true, meTherapist.getBody().get("isTherapist"));
        Assertions.assertEquals(true, meTherapist.getBody().get("isAdmin"));

        // 3. /clinic/me with invalid UUID subject throws 400
        Jwt invalidSubJwt = Jwt.withTokenValue("tok-invalid")
                .header("alg", "none")
                .claim("sub", "invalid-uuid")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(3600))
                .build();
        Authentication invalidSubAuth = Mockito.mock(Authentication.class);
        Mockito.when(invalidSubAuth.getPrincipal()).thenReturn(invalidSubJwt);
        Assertions.assertThrows(ResponseStatusException.class, () -> fullController.getCurrentUserContext(invalidSubAuth));

        // 4. /clinic/me with valid UUID, blank claim, and UPPERCASE roles
        UUID edgeUuid = UUID.randomUUID();
        Mockito.when(clientProfileRepository.findByUserId(edgeUuid)).thenReturn(Optional.empty());
        Mockito.when(providerProfileRepository.findByUserUuid(edgeUuid)).thenReturn(Optional.empty());
        Jwt edgeJwt = Jwt.withTokenValue("tok2")
                .header("alg", "none")
                .claim("sub", edgeUuid.toString())
                .claim("preferred_username", "   ")
                .claim("realm_access", Map.of("roles", List.of("THERAPIST", "ADMIN")))
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(3600))
                .build();
        Authentication edgeAuth = Mockito.mock(Authentication.class);
        Mockito.when(edgeAuth.getPrincipal()).thenReturn(edgeJwt);
        ResponseEntity<Map<String, Object>> meEdge = fullController.getCurrentUserContext(edgeAuth);
        Assertions.assertEquals(true, meEdge.getBody().get("isTherapist"));
        Assertions.assertEquals(true, meEdge.getBody().get("isAdmin"));

        // 5. /clinic/me with non-Jwt principal throws 401 & JWT without realm_access succeeds
        Authentication stringAuth = Mockito.mock(Authentication.class);
        Mockito.when(stringAuth.getPrincipal()).thenReturn("anonymousUser");
        Assertions.assertThrows(ResponseStatusException.class, () -> fullController.getCurrentUserContext(stringAuth));

        Jwt noRealmJwt = Jwt.withTokenValue("tok3")
                .header("alg", "none")
                .claim("sub", userUuid.toString())
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(3600))
                .build();
        Authentication noRealmAuth = Mockito.mock(Authentication.class);
        Mockito.when(noRealmAuth.getPrincipal()).thenReturn(noRealmJwt);
        Assertions.assertEquals(HttpStatus.OK, fullController.getCurrentUserContext(noRealmAuth).getStatusCode());

        // 6. /clinic/directory
        Organisation org = new Organisation("39485712", "Nordic Mind");
        org.setId(UUID.randomUUID());
        existingPp.setOrganisation(org);
        Service svc = new Service();
        svc.setId(UUID.randomUUID());
        svc.setName("EMDR Terapi");
        svc.setPrice(1150.0);
        svc.setLocation("København K");
        svc.setProvider(existingPp);
        svc.setOrganisation(org);

        Service orgOnlySvc = new Service();
        orgOnlySvc.setId(UUID.randomUUID());
        orgOnlySvc.setName("Fælles Klinikydelse");
        orgOnlySvc.setOrganisation(org);

        Mockito.when(organisationRepository.findAll()).thenReturn(List.of(org));
        Mockito.when(providerProfileRepository.findAll()).thenReturn(List.of(existingPp, new ProviderProfile()));
        Mockito.when(serviceRepository.findAll()).thenReturn(List.of(svc, orgOnlySvc, new Service()));

        ResponseEntity<Map<String, Object>> dirResp = fullController.getMarketplaceDirectory();
        Assertions.assertEquals(HttpStatus.OK, dirResp.getStatusCode());
        Assertions.assertNotNull(dirResp.getBody().get("organisations"));

        // Also test handshake name resolution when clientName/therapistName are null
        UUID enrichClientUuid = UUID.randomUUID();
        ClientProfile enrichCp = new ClientProfile(enrichClientUuid, "Anders Sand", "1993-04-18", "Vej 1", "2100", "+4511223344", "a@b.dk");
        Mockito.when(clientProfileRepository.findByUserId(enrichClientUuid)).thenReturn(Optional.of(enrichCp));
        ClientTherapistHandshake bareHandshake = new ClientTherapistHandshake();
        bareHandshake.setClientUuid(enrichClientUuid);
        bareHandshake.setTherapistUuid(userUuid);
        bareHandshake.setStatus("PENDING");
        Mockito.when(handshakeRepository.findByClientUuidAndTherapistUuid(enrichClientUuid, userUuid)).thenReturn(Optional.empty());
        ResponseEntity<ClientTherapistHandshake> enrichedHandshake = fullController.createOrUpdateHandshake(bareHandshake);
        Assertions.assertEquals("Anders Sand", enrichedHandshake.getBody().getClientName());
        Assertions.assertEquals("Bo Jensen (Aut. Psykolog)", enrichedHandshake.getBody().getTherapistName());
        Assertions.assertEquals("Nordic Mind", enrichedHandshake.getBody().getOrganisationName());

        // 7. /clinic/client/{clientUuid}/overview (with matching and non-matching provider/service/org)
        UUID clientUuid = UUID.randomUUID();
        UUID unknownTherapistUuid = UUID.randomUUID();
        UUID noOrgTherapistUuid = UUID.randomUUID();
        ProviderProfile noOrgProvider = new ProviderProfile();
        noOrgProvider.setUserUuid(noOrgTherapistUuid);
        noOrgProvider.setDisplayName("Dr. Solo");
        Mockito.when(providerProfileRepository.findAll()).thenReturn(List.of(existingPp, noOrgProvider, new ProviderProfile()));

        Appointment apt1 = new Appointment();
        apt1.setId(UUID.randomUUID());
        apt1.setClientUuid(clientUuid);
        apt1.setTherapistUuid(userUuid);
        apt1.setServiceId(svc.getId());
        apt1.setStartTime(LocalDateTime.now().plusDays(1));
        apt1.setEndTime(LocalDateTime.now().plusDays(1).plusHours(1));
        apt1.setTitle("EMDR Session");
        apt1.setStatus("SCHEDULED");

        Appointment apt2 = new Appointment();
        apt2.setId(UUID.randomUUID());
        apt2.setClientUuid(clientUuid);
        apt2.setTherapistUuid(unknownTherapistUuid);
        apt2.setServiceId(null);
        apt2.setStartTime(LocalDateTime.now().plusDays(2));
        apt2.setEndTime(LocalDateTime.now().plusDays(2).plusHours(1));
        apt2.setTitle("Fallback Session");
        apt2.setStatus("CANCELLED");

        Appointment apt3 = new Appointment();
        apt3.setId(UUID.randomUUID());
        apt3.setClientUuid(clientUuid);
        apt3.setTherapistUuid(noOrgTherapistUuid);
        apt3.setServiceId(UUID.randomUUID());
        apt3.setStartTime(LocalDateTime.now().plusDays(1));
        apt3.setEndTime(LocalDateTime.now().plusDays(1).plusHours(2));
        apt3.setTitle("Solo Session");
        apt3.setStatus("SCHEDULED");

        Mockito.when(appointmentRepository.findByClientUuid(clientUuid)).thenReturn(List.of(apt1, apt2, apt3));

        ClientTherapistHandshake hs1 = new ClientTherapistHandshake(clientUuid, "Anders", userUuid, "Bo", "Nordic Mind", "ACTIVE");
        hs1.setId(UUID.randomUUID());
        ClientTherapistHandshake hs2 = new ClientTherapistHandshake(clientUuid, "Anders", unknownTherapistUuid, null, null, "PENDING");
        hs2.setId(UUID.randomUUID());
        ClientTherapistHandshake hs3 = new ClientTherapistHandshake(clientUuid, "Anders", noOrgTherapistUuid, "Solo", "Solo", "REVOKED");
        hs3.setId(UUID.randomUUID());
        Mockito.when(handshakeRepository.findByClientUuid(clientUuid)).thenReturn(List.of(hs1, hs2, hs3));

        JournalEntry je = new JournalEntry("Titel", "Tekst", 9, LocalDateTime.now(), clientUuid);
        Mockito.when(journalEntryRepository.findByClientUuidOrderByTimestampDesc(clientUuid)).thenReturn(List.of(je));

        com.platform.crm.model.PsychotraumatologyTest pcl = new com.platform.crm.model.PsychotraumatologyTest();
        pcl.setClientUuid(clientUuid);
        pcl.setScore(24);
        pcl.setSeverity("Moderate Symptoms");
        Mockito.when(pcl5Repository.findByClientUuid(clientUuid)).thenReturn(List.of(pcl));

        ResponseEntity<Map<String, Object>> clientOverview = fullController.getClientOverview(clientUuid);
        Assertions.assertEquals(HttpStatus.OK, clientOverview.getStatusCode());
        Assertions.assertEquals(3, ((List<?>) clientOverview.getBody().get("appointments")).size());

        // Also test getClientOverview on 5-arg controller (null optional repos)
        Assertions.assertEquals(HttpStatus.OK, controller.getClientOverview(clientUuid).getStatusCode());

        // 8. /clinic/therapist/{therapistUuid}/overview
        ClientProfile cp1 = new ClientProfile(clientUuid, "Anders Sand", "1993-04-18", "Nørrebrogade", "2200", "+4511223344", "client@mail.com");
        ClientProfile cpNoDob = new ClientProfile();
        UUID client2Uuid = UUID.randomUUID();
        cpNoDob.setUserId(client2Uuid);
        cpNoDob.setFullName("User Test");
        ClientProfile cpSelfTherapist = new ClientProfile(userUuid, "Bo Jensen", "1984-02-24", "Bredgade", "1260", "+4540112233", "bjens@mail.com");
        ClientProfile cpNullUser = new ClientProfile();

        Mockito.when(clientProfileRepository.findAll()).thenReturn(List.of(cp1, cpNoDob, cpSelfTherapist, cpNullUser));
        Mockito.when(appointmentRepository.findByTherapistUuid(userUuid)).thenReturn(List.of(apt1, apt2, apt3));
        Mockito.when(handshakeRepository.findByTherapistUuid(userUuid)).thenReturn(List.of(hs1));
        Mockito.when(journalEntryRepository.findByClientUuidOrderByTimestampDesc(client2Uuid)).thenReturn(List.of());
        Mockito.when(pcl5Repository.findByClientUuid(client2Uuid)).thenReturn(List.of());

        ResponseEntity<Map<String, Object>> therapistOverview = fullController.getTherapistOverview(userUuid);
        Assertions.assertEquals(HttpStatus.OK, therapistOverview.getStatusCode());
        List<?> roster = (List<?>) therapistOverview.getBody().get("clients");
        Assertions.assertEquals(2, roster.size());

        // Non-existing therapist overview & 5-arg controller
        Assertions.assertEquals(HttpStatus.OK, controller.getTherapistOverview(UUID.randomUUID()).getStatusCode());

        // Update client profile endpoint
        Assertions.assertNotNull(fullController.updateClientProfile(cp1));
    }

    @Test
    public void testClinicDatabaseInitializerFullRun() throws Exception {
        I18nDictionaryRepository i18nRepo = Mockito.mock(I18nDictionaryRepository.class);

        I18nDictionary dictWithNullMap = new I18nDictionary("en", null);
        Mockito.when(i18nRepo.findByLocale("en")).thenReturn(Optional.of(dictWithNullMap));
        Mockito.when(i18nRepo.findByLocale("da")).thenReturn(Optional.empty());
        Mockito.when(i18nRepo.findByLocale("fr")).thenReturn(Optional.empty());
        Mockito.when(i18nRepo.findByLocale("es")).thenReturn(Optional.empty());
        Mockito.when(i18nRepo.findByLocale("de"))
                .thenReturn(Optional.empty())
                .thenThrow(new RuntimeException("Simulated Mongo timeout"));

        com.platform.config.ClinicDatabaseInitializer initializer = new com.platform.config.ClinicDatabaseInitializer(i18nRepo);

        initializer.run();
        initializer.run();
    }

    @Test
    public void testKeycloakSoleRoleAuthorityInSecurityConfigAndControllers() {
        UUID userUuid = UUID.randomUUID();

        // Even if a ProviderProfile exists in PostgreSQL, business_db must NEVER grant roles; only Keycloak JWT decides roles
        ProviderProfile existingProvider = new ProviderProfile();
        existingProvider.setId(UUID.randomUUID());
        existingProvider.setUserUuid(userUuid);
        existingProvider.setDisplayName("Dr. Keycloak Decides");

        Mockito.when(providerProfileRepository.findByUserUuid(userUuid))
                .thenReturn(Optional.of(existingProvider));
        Mockito.when(clientProfileRepository.findByUserId(userUuid))
                .thenReturn(Optional.empty());

        com.platform.config.SecurityConfig secConfig = new com.platform.config.SecurityConfig();

        // 1. Keycloak JWT with ONLY "client" role -> does NOT get ROLE_therapist even though ProviderProfile exists in DB
        Jwt clientOnlyJwt = Jwt.withTokenValue("client-only-jwt")
                .header("alg", "none")
                .claim("sub", userUuid.toString())
                .claim("realm_access", Map.of("roles", List.of("client")))
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(3600))
                .build();
        var clientOnlyAuth = secConfig.jwtAuthenticationConverter().convert(clientOnlyJwt);
        Assertions.assertNotNull(clientOnlyAuth);
        Assertions.assertTrue(clientOnlyAuth.getAuthorities().stream()
                .anyMatch(a -> "ROLE_client".equals(a.getAuthority())));
        Assertions.assertFalse(clientOnlyAuth.getAuthorities().stream()
                .anyMatch(a -> "ROLE_therapist".equals(a.getAuthority())));

        ResponseEntity<Map<String, Object>> meClientOnly = controller.getCurrentUserContext(clientOnlyAuth);
        Assertions.assertEquals(HttpStatus.OK, meClientOnly.getStatusCode());
        Assertions.assertEquals(false, meClientOnly.getBody().get("isTherapist"));

        // 2. Keycloak JWT with "therapist" role -> gets ROLE_therapist and isTherapist = true
        Jwt keycloakTherapistJwt = Jwt.withTokenValue("kc-therapist-jwt")
                .header("alg", "none")
                .claim("sub", userUuid.toString())
                .claim("realm_access", Map.of("roles", List.of("client", "therapist")))
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(3600))
                .build();
        var keycloakTherapistAuth = secConfig.jwtAuthenticationConverter().convert(keycloakTherapistJwt);
        Assertions.assertNotNull(keycloakTherapistAuth);
        Assertions.assertTrue(keycloakTherapistAuth.getAuthorities().stream()
                .anyMatch(a -> "ROLE_therapist".equals(a.getAuthority())));

        ResponseEntity<Map<String, Object>> meTherapist = controller.getCurrentUserContext(keycloakTherapistAuth);
        Assertions.assertEquals(HttpStatus.OK, meTherapist.getStatusCode());
        Assertions.assertEquals(true, meTherapist.getBody().get("isTherapist"));

        // 3. JWT with malformed realm_access returns empty authorities
        Jwt malformedRealmJwt = Jwt.withTokenValue("malformed-jwt")
                .header("alg", "none")
                .claim("sub", userUuid.toString())
                .claim("realm_access", Map.of("roles", "not-a-list"))
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(3600))
                .build();
        var malformedAuth = secConfig.jwtAuthenticationConverter().convert(malformedRealmJwt);
        Assertions.assertNotNull(malformedAuth);
        Assertions.assertTrue(malformedAuth.getAuthorities().isEmpty());

        // 4. JournalController strictly enforces Keycloak role
        JournalEntryRepository entryRepo = Mockito.mock(JournalEntryRepository.class);
        JournalGitService gitService = Mockito.mock(JournalGitService.class);
        TherapistClinicalNoteRepository noteRepo = Mockito.mock(TherapistClinicalNoteRepository.class);
        Mockito.when(entryRepo.findByClientUuidOrderByTimestampDesc(userUuid)).thenReturn(List.of());
        Mockito.when(noteRepo.findByClientUuidOrderByCreatedAtDesc(userUuid)).thenReturn(List.of(new TherapistClinicalNote()));

        JournalController jc = new JournalController(entryRepo, gitService, noteRepo, new JournalService());
        Map<String, Object> bundleForClientOnly = jc.getClientJournalBundle(userUuid, clientOnlyAuth).getBody();
        Assertions.assertEquals(0, ((List<?>) bundleForClientOnly.get("clinicalNotes")).size());

        Map<String, Object> bundleForKeycloakTherapist = jc.getClientJournalBundle(userUuid, keycloakTherapistAuth).getBody();
        Assertions.assertEquals(1, ((List<?>) bundleForKeycloakTherapist.get("clinicalNotes")).size());
    }

    @Test
    public void testPsychotraumatologyProfilePublicHealthAndSecureDataControllers() {
        // 1. PsychotraumatologyController
        com.platform.crm.repository.PsychotraumatologyTestRepository pclRepo =
                Mockito.mock(com.platform.crm.repository.PsychotraumatologyTestRepository.class);
        Mockito.when(pclRepo.save(any(com.platform.crm.model.PsychotraumatologyTest.class)))
                .thenAnswer(i -> i.getArgument(0));

        PsychotraumatologyController pclController = new PsychotraumatologyController(pclRepo);
        UUID clientUuid = UUID.randomUUID();
        UUID therapistUuid = UUID.randomUUID();

        com.platform.dto.PsychotraumatologyTestDto dto = new com.platform.dto.PsychotraumatologyTestDto();
        dto.setClientUuid(clientUuid);
        dto.setTherapistUuid(therapistUuid);
        dto.setTestType("PCL-5");
        dto.setScore(35);
        dto.setSeverity("Moderate to High");
        dto.setAnswersJson("{\"q1\":2}");

        ResponseEntity<com.platform.crm.model.PsychotraumatologyTest> savedTest = pclController.submitTest(dto);
        Assertions.assertEquals(HttpStatus.OK, savedTest.getStatusCode());
        Assertions.assertEquals(35, savedTest.getBody().getScore());

        // Null auth & non-Jwt principal throw 401
        Assertions.assertThrows(ResponseStatusException.class, () -> pclController.getTestsByClient(clientUuid, null));
        Authentication nonJwtAuth = Mockito.mock(Authentication.class);
        Mockito.when(nonJwtAuth.getPrincipal()).thenReturn("anonymous");
        Assertions.assertThrows(ResponseStatusException.class, () -> pclController.getTestsByClient(clientUuid, nonJwtAuth));

        // Caller != clientUuid throws 403
        Jwt otherJwt = Jwt.withTokenValue("other")
                .header("alg", "none")
                .claim("sub", UUID.randomUUID().toString())
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(3600))
                .build();
        Authentication otherAuth = Mockito.mock(Authentication.class);
        Mockito.when(otherAuth.getPrincipal()).thenReturn(otherJwt);
        Assertions.assertThrows(ResponseStatusException.class, () -> pclController.getTestsByClient(clientUuid, otherAuth));

        // Matching callerUuid returns 200 OK
        Jwt ownerJwt = Jwt.withTokenValue("owner")
                .header("alg", "none")
                .claim("sub", clientUuid.toString())
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(3600))
                .build();
        Authentication ownerAuth = Mockito.mock(Authentication.class);
        Mockito.when(ownerAuth.getPrincipal()).thenReturn(ownerJwt);
        Mockito.when(pclRepo.findByClientUuid(clientUuid)).thenReturn(List.of(savedTest.getBody()));
        Assertions.assertEquals(1, pclController.getTestsByClient(clientUuid, ownerAuth).getBody().size());

        // 2. ProfileController
        ProfileController profileController = new ProfileController();
        Assertions.assertThrows(ResponseStatusException.class, () -> profileController.getUserProfile(null));
        Assertions.assertThrows(ResponseStatusException.class, () -> profileController.getUserProfile(nonJwtAuth));
        ResponseEntity<Map<String, String>> profResp = profileController.getUserProfile(ownerAuth);
        Assertions.assertEquals(HttpStatus.OK, profResp.getStatusCode());
        Assertions.assertEquals(clientUuid.toString(), profResp.getBody().get("userUuid"));

        // 3. PublicHealthController & SecureDataController
        PublicHealthController healthController = new PublicHealthController();
        Assertions.assertEquals("UP", healthController.healthCheck().getBody().get("status"));

        SecureDataController secureDataController = new SecureDataController();
        Assertions.assertNotNull(secureDataController.getSecureData().getBody().get("data"));
    }
}

