package com.platform.features.clinic;

import com.platform.config.PlatformCacheService;
import com.platform.config.PlatformEventService;
import com.platform.config.SecurityConfig;
import com.platform.core.BaseDomainCore.DomainException;
import com.platform.features.appointment.AppointmentFeature.Appointment;
import com.platform.features.appointment.AppointmentFeature.AppointmentPort;
import com.platform.features.clinic.ClinicPlatformFeature.ClientProfile;
import com.platform.features.clinic.ClinicPlatformFeature.ClientProfileDto;
import com.platform.features.clinic.ClinicPlatformFeature.ClientProfileRepository;
import com.platform.features.clinic.ClinicPlatformFeature.ClientTherapistHandshake;
import com.platform.features.clinic.ClinicPlatformFeature.ClientTherapistHandshakeDto;
import com.platform.features.clinic.ClinicPlatformFeature.ClinicPlatformController;
import com.platform.features.clinic.ClinicPlatformFeature.ClinicPlatformService;
import com.platform.features.clinic.ClinicPlatformFeature.HandshakeRepository;
import com.platform.features.clinic.ClinicPlatformFeature.HandshakeRequestDto;
import com.platform.features.clinic.ClinicPlatformFeature.OnboardingRequestDto;
import com.platform.features.clinic.ClinicPlatformFeature.Organisation;
import com.platform.features.clinic.ClinicPlatformFeature.OrganisationDto;
import com.platform.features.clinic.ClinicPlatformFeature.OrganisationRepository;
import com.platform.features.clinic.ClinicPlatformFeature.ProviderProfile;
import com.platform.features.clinic.ClinicPlatformFeature.ProviderProfileDto;
import com.platform.features.clinic.ClinicPlatformFeature.ProviderProfileRepository;
import com.platform.features.clinic.ClinicPlatformFeature.Service;
import com.platform.features.clinic.ClinicPlatformFeature.ServiceDto;
import com.platform.features.clinic.ClinicPlatformFeature.ServiceRepository;
import com.platform.features.journal.JournalFeature.JournalEntry;
import com.platform.features.journal.JournalFeature.JournalPort;
import com.platform.features.psychotraumatology.PsychotraumatologyFeature.PsychotraumatologyPort;
import com.platform.features.psychotraumatology.PsychotraumatologyFeature.PsychotraumatologyTest;
import com.platform.features.user.UserFeature.UserEntity;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.filter.OncePerRequestFilter;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Currency;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;

public class ClinicPlatformServiceTest {

    private ClientProfileRepository clientProfileRepository;
    private OrganisationRepository organisationRepository;
    private ProviderProfileRepository providerProfileRepository;
    private ServiceRepository serviceRepository;
    private HandshakeRepository handshakeRepository;
    private ClinicPlatformService service;
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

        service = new ClinicPlatformService(
                clientProfileRepository,
                organisationRepository,
                providerProfileRepository,
                serviceRepository,
                handshakeRepository
        );
        controller = new ClinicPlatformController(service);
    }

    @Test
    public void testClientProfileCreateAndUpdateAndGet() {
        UUID userId = UUID.randomUUID();
        ClientProfile profile = new ClientProfile(userId, "Anna Jensen", "1992-05-14", "Nørrebrogade 10", "2200", "+4512345678", "anna@example.com");
        profile.setId(UUID.randomUUID());

        ClientProfileDto dtoRecord = service.toDto(profile);
        Assertions.assertEquals(profile.getId(), dtoRecord.id());
        Assertions.assertEquals(userId, dtoRecord.userId());
        Assertions.assertEquals(profile.getId(), service.toEntity(dtoRecord).getId());
        Assertions.assertNull(service.toDto(null));
        Assertions.assertNull(service.toEntity(null));

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
        Assertions.assertThrows(DomainException.class, () -> controller.onboardTherapist(Map.of("cvrNumber", "   ")));

        // Missing org name for new CVR throws 400
        Mockito.when(organisationRepository.findByCvrNumber("99999999")).thenReturn(Optional.empty());
        Assertions.assertThrows(DomainException.class, () -> controller.onboardTherapist(Map.of("cvrNumber", "99999999")));

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

        // Test authenticated client onboarding via onboardTherapist(payload, auth)
        UUID upgradingClientUuid = UUID.randomUUID();
        Jwt clientJwt = Jwt.withTokenValue("client-tok")
                .header("alg", "none")
                .claim("sub", upgradingClientUuid.toString())
                .claim("realm_access", Map.of("roles", List.of("client")))
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(3600))
                .build();
        Authentication clientAuth = new JwtAuthenticationToken(
                clientJwt,
                List.of(new SimpleGrantedAuthority("ROLE_client"))
        );

        ResponseEntity<ProviderProfile> upgraded = controller.onboardTherapist(Map.of("cvrNumber", "87654321"), clientAuth);
        Assertions.assertEquals(HttpStatus.OK, upgraded.getStatusCode());
        Assertions.assertEquals(upgradingClientUuid, upgraded.getBody().getUserUuid());
    }

    @Test
    public void testServiceCatalogCreateAndToggle() {
        UUID providerId = UUID.randomUUID();
        UUID therapistUuid = UUID.randomUUID();
        UUID orgId = UUID.randomUUID();
        ProviderProfile provider = new ProviderProfile();
        provider.setId(providerId);
        provider.setUserUuid(therapistUuid);
        provider.setWorkingHoursJson("Man-Fre 09:00 - 17:00");
        Organisation org = new Organisation("12345678", "Clinic");
        org.setId(orgId);
        org.setIban("DK5000400440116243");
        provider.setOrganisation(org);

        ClientProfile clientProfile = new ClientProfile();
        clientProfile.setUserId(therapistUuid);
        clientProfile.setFullName("Test Therapist");
        clientProfile.setHomeAddress("Strandvejen 10");
        clientProfile.setPostalCode("2100");
        clientProfile.setPhoneNumber("+4512345678");

        Mockito.when(providerProfileRepository.findById(providerId)).thenReturn(Optional.of(provider));
        Mockito.when(providerProfileRepository.findByUserUuid(therapistUuid)).thenReturn(Optional.of(provider));
        Mockito.when(clientProfileRepository.findByUserId(therapistUuid)).thenReturn(Optional.of(clientProfile));
        Mockito.when(organisationRepository.findById(orgId)).thenReturn(Optional.of(org));

        Map<String, Object> req = new HashMap<>();
        req.put("name", "Traumeterapi (PCL-5 Opfølgning)");
        req.put("description", "Individuel session");
        req.put("durationMinutes", 60);
        req.put("price", 2.0);
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

        Assertions.assertThrows(DomainException.class, () -> controller.toggleServiceActive(UUID.randomUUID()));
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

        Assertions.assertThrows(DomainException.class, () -> controller.updateHandshakeStatus(UUID.randomUUID(), Map.of("status", "REVOKED")));
    }

    @Test
    public void testClinicMeDirectoryAndOverviews() {
        AppointmentPort appointmentRepository = Mockito.mock(AppointmentPort.class);
        JournalPort journalEntryRepository = Mockito.mock(JournalPort.class);
        PsychotraumatologyPort pcl5Repository = Mockito.mock(PsychotraumatologyPort.class);

        ClinicPlatformService fullService = new ClinicPlatformService(
                clientProfileRepository,
                organisationRepository,
                providerProfileRepository,
                serviceRepository,
                handshakeRepository,
                appointmentRepository,
                journalEntryRepository,
                pcl5Repository
        );
        ClinicPlatformController fullController = new ClinicPlatformController(fullService);

        // 1. /clinic/me with null auth throws 401
        Assertions.assertThrows(DomainException.class, () -> fullController.getCurrentUserContext(null));

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

        // 3. /clinic/me with invalid UUID subject throws 401
        Jwt invalidSubJwt = Jwt.withTokenValue("tok-invalid")
                .header("alg", "none")
                .claim("sub", "invalid-uuid")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(3600))
                .build();
        Authentication invalidSubAuth = Mockito.mock(Authentication.class);
        Mockito.when(invalidSubAuth.getPrincipal()).thenReturn(invalidSubJwt);
        Assertions.assertThrows(DomainException.class, () -> fullController.getCurrentUserContext(invalidSubAuth));

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
        Assertions.assertThrows(DomainException.class, () -> fullController.getCurrentUserContext(stringAuth));

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
        svc.setPrice(2.0);
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

        // 7. /clinic/client/{clientUuid}/overview
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

        PsychotraumatologyTest pcl = new PsychotraumatologyTest();
        pcl.setClientUuid(clientUuid);
        pcl.setScore(24);
        pcl.setSeverity("Moderate Symptoms");
        Mockito.when(pcl5Repository.findByClientUuid(clientUuid)).thenReturn(List.of(pcl));

        ResponseEntity<Map<String, Object>> clientOverview = fullController.getClientOverview(clientUuid);
        Assertions.assertEquals(HttpStatus.OK, clientOverview.getStatusCode());
        Assertions.assertEquals(3, ((List<?>) clientOverview.getBody().get("appointments")).size());
        Assertions.assertEquals(3, ((List<?>) clientOverview.getBody().get("services")).size());

        // Therapist viewing their own personal client overview must not see their own services
        ResponseEntity<Map<String, Object>> therapistOwnClientOverview = fullController.getClientOverview(userUuid);
        Assertions.assertEquals(HttpStatus.OK, therapistOwnClientOverview.getStatusCode());
        Assertions.assertEquals(1, ((List<?>) therapistOwnClientOverview.getBody().get("services")).size());

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
        Appointment aptClient2 = new Appointment();
        aptClient2.setId(UUID.randomUUID());
        aptClient2.setClientUuid(client2Uuid);
        aptClient2.setTherapistUuid(userUuid);
        aptClient2.setStartTime(LocalDateTime.now().plusDays(3));
        aptClient2.setStatus("SCHEDULED");
        Mockito.when(appointmentRepository.findByTherapistUuid(userUuid)).thenReturn(List.of(apt1, apt2, apt3, aptClient2));
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
    public void testKeycloakSoleRoleAuthorityInSecurityConfigAndControllers() throws Exception {
        UUID userUuid = UUID.randomUUID();

        ProviderProfile existingProvider = new ProviderProfile();
        existingProvider.setId(UUID.randomUUID());
        existingProvider.setUserUuid(userUuid);
        existingProvider.setDisplayName("Dr. Keycloak Decides");

        Mockito.when(providerProfileRepository.findByUserUuid(userUuid))
                .thenReturn(Optional.of(existingProvider));
        Mockito.when(clientProfileRepository.findByUserId(userUuid))
                .thenReturn(Optional.empty());

        SecurityConfig secConfig = new SecurityConfig();

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

        OncePerRequestFilter bffFilter = ReflectionTestUtils.invokeMethod(secConfig, "bffSessionHeaderFilter");
        HttpServletRequest mockReq = Mockito.mock(HttpServletRequest.class);
        HttpServletResponse mockResp = Mockito.mock(HttpServletResponse.class);
        FilterChain mockChain = Mockito.mock(FilterChain.class);

        SecurityContextHolder.getContext().setAuthentication(keycloakTherapistAuth);
        bffFilter.doFilter(mockReq, mockResp, mockChain);

        SecurityContextHolder.clearContext();
        Mockito.when(mockReq.getHeader("X-Auth-User-Uuid")).thenReturn(userUuid.toString());
        Mockito.when(mockReq.getHeader("X-Auth-Roles")).thenReturn("client, ,therapist");
        bffFilter.doFilter(mockReq, mockResp, mockChain);

        Jwt noRealmJwt = Jwt.withTokenValue("tok")
                .header("alg", "none")
                .claim("sub", userUuid.toString())
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(3600))
                .build();
        secConfig.jwtAuthenticationConverter().convert(noRealmJwt);
        SecurityContextHolder.clearContext();
    }

    @Test
    public void testCreateServiceProfileValidation() {
        UUID therapistId = UUID.randomUUID();
        UUID providerProfileId = UUID.randomUUID();

        Map<String, Object> payload = new HashMap<>();
        payload.put("name", "Trauma Therapy");
        payload.put("description", "EMDR session");
        payload.put("price", "2.0");
        payload.put("durationMinutes", 60);
        payload.put("location", "København");
        payload.put("therapistUuid", therapistId.toString());

        DomainException ex = Assertions.assertThrows(DomainException.class, () -> controller.createService(payload));
        Assertions.assertEquals(400, ex.getStatus());
        Assertions.assertTrue(ex.getMessage().contains("CVR-nummer"));
        Assertions.assertTrue(ex.getMessage().contains("organisationsnavn"));
        Assertions.assertTrue(ex.getMessage().contains("arbejdstider"));
        Assertions.assertTrue(ex.getMessage().contains("hjemmeadresse"));
        Assertions.assertTrue(ex.getMessage().contains("postnummer"));
        Assertions.assertTrue(ex.getMessage().contains("telefonnummer"));
        Assertions.assertTrue(ex.getMessage().contains("IBAN"));

        Organisation org = new Organisation("12345678", "Psykologhuset");
        org.setId(UUID.randomUUID());
        org.setIban("DK5000400440116243");
        ProviderProfile pp = new ProviderProfile();
        pp.setId(providerProfileId);
        pp.setUserUuid(therapistId);
        pp.setOrganisation(org);
        pp.setWorkingHoursJson("Man-Fre 09:00 - 16:00");

        ClientProfile cp = new ClientProfile();
        cp.setUserId(therapistId);
        cp.setFullName("Dr. Jensen");
        cp.setHomeAddress("Vesterbrogade 12");
        cp.setPostalCode("1620");
        cp.setPhoneNumber("+4512345678");

        Mockito.when(providerProfileRepository.findByUserUuid(therapistId)).thenReturn(Optional.of(pp));
        Mockito.when(providerProfileRepository.findById(providerProfileId)).thenReturn(Optional.of(pp));
        Mockito.when(clientProfileRepository.findByUserId(therapistId)).thenReturn(Optional.of(cp));
        Mockito.when(serviceRepository.save(any(Service.class))).thenAnswer(i -> {
            Service s = i.getArgument(0);
            if (s.getId() == null) s.setId(UUID.randomUUID());
            return s;
        });

        payload.put("providerProfileId", providerProfileId.toString());
        ResponseEntity<Service> created = controller.createService(payload);
        Assertions.assertEquals(HttpStatus.CREATED, created.getStatusCode());
        Assertions.assertNotNull(created.getBody());
        Assertions.assertEquals("Trauma Therapy", created.getBody().getName());
    }

    @Test
    public void testAdditionalServiceHandshakeAndBranchCoverage() {
        UUID therapistId = UUID.randomUUID();
        UUID providerProfileId = UUID.randomUUID();
        UUID orgId = UUID.randomUUID();
        UUID serviceId = UUID.randomUUID();
        UUID clientUuid = UUID.randomUUID();

        Organisation org = new Organisation("12345678", "Psykologhuset");
        org.setId(orgId);
        org.setAddress("Vesterbrogade 12");
        org.setPostalCode("1620");
        org.setPhoneNumber("+4512345678");
        org.setEmail("info@psykologhuset.dk");
        org.setVideoMeetingUrl("https://meet.jit.si/org-room");
        org.setIban("DK5000400440116243");

        ProviderProfile pp = new ProviderProfile();
        pp.setId(providerProfileId);
        pp.setUserUuid(therapistId);
        pp.setDisplayName("Dr. Jensen");
        pp.setOrganisation(org);
        pp.setWorkingHoursJson("Man-Fre 09:00 - 16:00");

        ClientProfile cp = new ClientProfile(clientUuid, "Anders Sand", "1993-04-18", "Nørrebrogade 10", "2200", "+4511223344", "anders@mail.com");

        Service existingService = new Service();
        existingService.setId(serviceId);
        existingService.setName("Trauma Therapy");
        existingService.setDescription("Desc");
        existingService.setDurationMinutes(60);
        existingService.setPrice(2.0);
        existingService.setLocation("Online Video");
        existingService.setOrganisation(org);
        existingService.setProvider(pp);

        Service duplicateService = new Service();
        duplicateService.setId(UUID.randomUUID());
        duplicateService.setName("Trauma Therapy");
        duplicateService.setDescription("Dup");
        duplicateService.setDurationMinutes(60);
        duplicateService.setPrice(2.0);
        duplicateService.setLocation("Online");
        duplicateService.setOrganisation(org);
        duplicateService.setProvider(pp);

        Mockito.when(organisationRepository.findById(orgId)).thenReturn(Optional.of(org));
        Mockito.when(organisationRepository.findByCvrNumber("12345678")).thenReturn(Optional.of(org));
        Mockito.when(providerProfileRepository.findById(providerProfileId)).thenReturn(Optional.of(pp));
        Mockito.when(providerProfileRepository.findByUserUuid(therapistId)).thenReturn(Optional.of(pp));
        Mockito.when(clientProfileRepository.findByUserId(therapistId)).thenReturn(Optional.of(cp));
        Mockito.when(clientProfileRepository.findByUserId(clientUuid)).thenReturn(Optional.of(cp));
        Mockito.when(serviceRepository.findById(serviceId)).thenReturn(Optional.of(existingService));
        Mockito.when(serviceRepository.findAll()).thenReturn(List.of(existingService, duplicateService));

        Map<String, String> onboardPayload = new HashMap<>();
        onboardPayload.put("cvrNumber", "12345678");
        onboardPayload.put("organisationName", "Psykologhuset Updated");
        onboardPayload.put("address", "Ny Adresse 1");
        onboardPayload.put("postalCode", "1000");
        onboardPayload.put("phoneNumber", "+4599887766");
        onboardPayload.put("email", "ny@klinik.dk");
        onboardPayload.put("videoMeetingUrl", "https://meet.jit.si/custom-room");
        onboardPayload.put("userUuid", "not-a-valid-uuid");
        Assertions.assertEquals(HttpStatus.OK, controller.onboardTherapist(onboardPayload).getStatusCode());

        Map<String, Object> createPayload = new HashMap<>();
        createPayload.put("name", "Trauma Therapy");
        createPayload.put("description", "Updated Desc");
        createPayload.put("price", 3.0);
        createPayload.put("durationMinutes", 60);
        createPayload.put("location", "Online Video");
        createPayload.put("userUuid", therapistId.toString());
        Assertions.assertEquals(HttpStatus.CREATED, controller.createService(createPayload).getStatusCode());

        Map<String, Object> updatePayload = new HashMap<>();
        updatePayload.put("nameDa", "Trauma Therapy v2");
        updatePayload.put("descriptionDa", "Updated Description Da");
        updatePayload.put("durationMinutes", 90);
        updatePayload.put("priceDkk", 4.0);
        updatePayload.put("location", "Hjemmebesøg hos klienten");
        updatePayload.put("isActive", true);
        updatePayload.put("providerProfileId", providerProfileId.toString());
        updatePayload.put("organisationId", orgId.toString());
        updatePayload.put("userUuid", therapistId.toString());
        ResponseEntity<Service> updatedSvc = controller.updateService(serviceId, updatePayload, null);
        Assertions.assertEquals(HttpStatus.OK, updatedSvc.getStatusCode());
        Assertions.assertEquals(90, updatedSvc.getBody().getDurationMinutes());

        Assertions.assertThrows(DomainException.class, () -> controller.updateService(UUID.randomUUID(), updatePayload, null));
        Service bareService = new Service();
        UUID bareId = UUID.randomUUID();
        Mockito.when(serviceRepository.findById(bareId)).thenReturn(Optional.of(bareService));
        Assertions.assertThrows(DomainException.class, () -> controller.updateService(bareId, Map.of(), null));

        ResponseEntity<Map<String, Object>> delResp = controller.deleteService(serviceId);
        Assertions.assertEquals(HttpStatus.OK, delResp.getStatusCode());
        Assertions.assertThrows(DomainException.class, () -> controller.deleteService(UUID.randomUUID()));

        ClientTherapistHandshake hs = new ClientTherapistHandshake(clientUuid, "Anders", therapistId, "Dr. Jensen", "Psykologhuset", "ACTIVE");
        hs.setId(UUID.randomUUID());
        hs.setValidFrom(LocalDate.now().minusDays(5));
        hs.setValidTo(LocalDate.now().plusDays(5));
        hs.setHistoryStartDate(LocalDate.now().minusDays(10));
        hs.setHistoryEndDate(LocalDate.now().plusDays(10));
        Assertions.assertTrue(hs.isCurrentlyValid());
        Assertions.assertTrue(hs.isCommitInHistoricalRange(null));
        Assertions.assertTrue(hs.isCommitInHistoricalRange(Instant.now()));
        Assertions.assertFalse(hs.isCommitInHistoricalRange(Instant.now().minusSeconds(86400 * 30)));
        Assertions.assertFalse(hs.isCommitInHistoricalRange(Instant.now().plusSeconds(86400 * 30)));

        hs.setValidFrom(LocalDate.now().plusDays(1));
        Assertions.assertFalse(hs.isCurrentlyValid());
        hs.setValidFrom(LocalDate.now().minusDays(5));
        hs.setValidTo(LocalDate.now().minusDays(1));
        Assertions.assertFalse(hs.isCurrentlyValid());
        hs.setStatus("REVOKED");
        Assertions.assertFalse(hs.isCurrentlyValid());

        Mockito.when(handshakeRepository.findById(hs.getId())).thenReturn(Optional.of(hs));
        Map<String, String> hsDates = new HashMap<>();
        hsDates.put("status", "ACTIVE");
        hsDates.put("validFrom", "2026-01-01");
        hsDates.put("validTo", "2026-12-31");
        hsDates.put("historyStartDate", "2026-01-01");
        hsDates.put("historyEndDate", "2026-12-31");
        controller.updateHandshakeStatus(hs.getId(), hsDates);

        Map<String, String> hsBlankDates = new HashMap<>();
        hsBlankDates.put("validFrom", "");
        hsBlankDates.put("validTo", "");
        hsBlankDates.put("historyStartDate", "");
        hsBlankDates.put("historyEndDate", "");
        controller.updateHandshakeStatus(hs.getId(), hsBlankDates);

        hs.setValidFrom(LocalDate.of(2026, 1, 1));
        hs.setValidTo(LocalDate.of(2026, 12, 31));
        hs.setHistoryStartDate(LocalDate.of(2026, 1, 1));
        hs.setHistoryEndDate(LocalDate.of(2026, 12, 31));
        Mockito.when(handshakeRepository.findByClientUuidAndTherapistUuid(clientUuid, therapistId)).thenReturn(Optional.of(hs));
        controller.createOrUpdateHandshake(hs);

        AppointmentPort aptRepo = Mockito.mock(AppointmentPort.class);
        JournalPort entryRepo = Mockito.mock(JournalPort.class);

        Appointment onlineApt = new Appointment(clientUuid, therapistId, LocalDateTime.now().plusDays(1), LocalDateTime.now().plusDays(1).plusHours(1), "Online Session", "SCHEDULED");
        onlineApt.setId(UUID.randomUUID());
        onlineApt.setServiceId(serviceId);
        onlineApt.setLocation("Online Video");

        Appointment homeVisitApt = new Appointment(clientUuid, therapistId, LocalDateTime.now().plusDays(2), LocalDateTime.now().plusDays(2).plusHours(1), "Hjemmebesøg", "SCHEDULED");
        homeVisitApt.setId(UUID.randomUUID());
        homeVisitApt.setLocation("Hjemmebesøg hos klienten");

        Appointment blankLocApt = new Appointment(clientUuid, therapistId, LocalDateTime.now().plusDays(3), LocalDateTime.now().plusDays(3).plusHours(1), "Blank Loc", "SCHEDULED");
        blankLocApt.setId(UUID.randomUUID());
        blankLocApt.setServiceId(serviceId);
        blankLocApt.setLocation("   ");

        Mockito.when(aptRepo.findByClientUuid(clientUuid)).thenReturn(List.of(onlineApt, homeVisitApt, blankLocApt));
        Mockito.when(aptRepo.findByTherapistUuid(therapistId)).thenReturn(List.of(onlineApt, homeVisitApt, blankLocApt));
        Mockito.when(providerProfileRepository.findAll()).thenReturn(List.of(pp));
        Mockito.when(clientProfileRepository.findAll()).thenReturn(List.of(cp));
        Mockito.when(handshakeRepository.findByClientUuid(clientUuid)).thenReturn(List.of(hs));
        Mockito.when(handshakeRepository.findByTherapistUuid(therapistId)).thenReturn(List.of(hs));

        ClinicPlatformService richService = new ClinicPlatformService(
                clientProfileRepository, organisationRepository, providerProfileRepository,
                serviceRepository, handshakeRepository, aptRepo, entryRepo, null
        );
        ClinicPlatformController richCtrl = new ClinicPlatformController(richService);
        pp.setVideoMeetingUrl("  ");
        richCtrl.getClientOverview(clientUuid);
        richCtrl.getTherapistOverview(therapistId);

        pp.setVideoMeetingUrl("https://meet.jit.si/dr-jensen");
        richCtrl.getClientOverview(clientUuid);
        richCtrl.getTherapistOverview(therapistId);

        Jwt therapistJwt = Jwt.withTokenValue("t-tok")
                .header("alg", "none")
                .claim("sub", therapistId.toString())
                .claim("realm_access", Map.of("roles", List.of("therapist")))
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(3600))
                .build();
        Authentication therapistAuth = Mockito.mock(Authentication.class);
        Mockito.when(therapistAuth.getPrincipal()).thenReturn(therapistJwt);

        Map<String, Object> partialUpdate = new HashMap<>();
        partialUpdate.put("name", "Direct Name");
        partialUpdate.put("description", "Direct Desc");
        partialUpdate.put("price", 800.0);
        partialUpdate.put("therapistUuid", therapistId.toString());
        richCtrl.updateService(serviceId, partialUpdate, therapistAuth);

        Map<String, Object> authOnlyUpdate = new HashMap<>();
        richCtrl.updateService(serviceId, authOnlyUpdate, therapistAuth);
    }

    @Test
    public void testExhaustiveClinicPlatformBranchesAndInfrastructure() {
        UUID therapistUuid = UUID.randomUUID();
        UUID clientUuid = UUID.randomUUID();
        UUID orgId = UUID.randomUUID();
        UUID providerId = UUID.randomUUID();
        UUID serviceId = UUID.randomUUID();

        AppointmentPort aptRepo = Mockito.mock(AppointmentPort.class);
        JournalPort jeRepo = Mockito.mock(JournalPort.class);
        PsychotraumatologyPort pclRepo = Mockito.mock(PsychotraumatologyPort.class);

        ClinicPlatformService cps = new ClinicPlatformService(
                clientProfileRepository, organisationRepository, providerProfileRepository,
                serviceRepository, handshakeRepository, aptRepo, jeRepo, pclRepo
        );

        Authentication nonJwtAuth = Mockito.mock(Authentication.class);
        Mockito.when(nonJwtAuth.getPrincipal()).thenReturn("stringPrincipal");

        Jwt tJwt = Jwt.withTokenValue("tok")
                .header("alg", "none")
                .claim("sub", therapistUuid.toString())
                .claim("realm_access", Map.of("roles", List.of("ROLE_THERAPIST")))
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(3600))
                .build();
        Authentication tAuth = Mockito.mock(Authentication.class);
        Mockito.when(tAuth.getPrincipal()).thenReturn(tJwt);

        ProviderProfile pp1 = new ProviderProfile();
        pp1.setId(providerId);
        pp1.setUserUuid(therapistUuid);
        pp1.setDisplayName("Dr. 1");
        ProviderProfile pp2 = new ProviderProfile();
        pp2.setId(UUID.randomUUID());
        pp2.setUserUuid(therapistUuid);
        pp2.setDisplayName("Dr. 2");
        Mockito.when(providerProfileRepository.findAll()).thenReturn(List.of(pp1, pp2));

        Service s1 = new Service();
        s1.setId(serviceId);
        s1.setName("Svc 1");
        Service s2 = new Service();
        s2.setId(serviceId);
        s2.setName("Svc 2");
        Mockito.when(serviceRepository.findAll()).thenReturn(List.of(s1, s2));

        ClientProfile cp1 = new ClientProfile(clientUuid, null, "1990-01-01", "   ", "   ", "   ", null);
        ClientProfile cp2 = new ClientProfile(clientUuid, "Dup", "1990-01-01", "Addr", "2100", "123", "e@mail.com");
        Mockito.when(clientProfileRepository.findAll()).thenReturn(List.of(cp1, cp2));
        Mockito.when(clientProfileRepository.findByUserId(clientUuid)).thenReturn(Optional.of(cp1));

        Jwt rolePrefixedJwt = Jwt.withTokenValue("tok")
                .header("alg", "none")
                .claim("sub", clientUuid.toString())
                .claim("preferred_username", "user1")
                .claim("name", "Fallback Name")
                .claim("email", "fallback@mail.com")
                .claim("realm_access", Map.of("roles", List.of("ROLE_therapist", "ROLE_admin")))
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(3600))
                .build();
        Authentication rolePrefixedAuth = Mockito.mock(Authentication.class);
        Mockito.when(rolePrefixedAuth.getPrincipal()).thenReturn(rolePrefixedJwt);
        Map<String, Object> ctx = cps.getCurrentUserContext(rolePrefixedAuth);
        Assertions.assertEquals("Fallback Name", ctx.get("fullName"));
        Assertions.assertEquals("fallback@mail.com", ctx.get("email"));

        Appointment aptNullStart = new Appointment(null, therapistUuid, null, null, "No Start", "CONFIRMED");
        aptNullStart.setLocation("Hos klient");
        Appointment aptClientLoc = new Appointment(clientUuid, therapistUuid, LocalDateTime.now().plusDays(1), LocalDateTime.now().plusDays(1).plusHours(1), "Client Loc", "CONFIRMED");
        aptClientLoc.setLocation("At client home");
        ClientProfile cpNoPostal = new ClientProfile(clientUuid, "Name", "1990-01-01", "Main St 1", "   ", "12345678", "a@b.dk");
        Mockito.when(clientProfileRepository.findAll()).thenReturn(List.of(cpNoPostal, cp2));
        Mockito.when(aptRepo.findByTherapistUuid(therapistUuid)).thenReturn(List.of(aptNullStart, aptClientLoc));
        Mockito.when(providerProfileRepository.findByUserUuid(therapistUuid)).thenReturn(Optional.of(pp1));

        ClientTherapistHandshake activeHs = new ClientTherapistHandshake(clientUuid, "Name", therapistUuid, "Dr", "Org", "ACTIVE");
        Mockito.when(handshakeRepository.findByTherapistUuid(therapistUuid)).thenReturn(List.of(activeHs));
        Mockito.when(jeRepo.findByClientUuidOrderByTimestampDesc(clientUuid)).thenReturn(List.of());
        Mockito.when(pclRepo.findByClientUuid(clientUuid)).thenReturn(List.of());
        cps.getTherapistOverview(therapistUuid);
        controller.getTherapistOverview(therapistUuid);

        Map<String, String> nullFieldsPayload = new HashMap<>();
        nullFieldsPayload.put("cvrNumber", "12345678");
        nullFieldsPayload.put("organisationName", "");
        nullFieldsPayload.put("address", null);
        nullFieldsPayload.put("postalCode", null);
        nullFieldsPayload.put("phoneNumber", null);
        nullFieldsPayload.put("email", null);
        nullFieldsPayload.put("videoMeetingUrl", null);
        nullFieldsPayload.put("userUuid", "undefined");
        Organisation existingOrg = new Organisation("12345678", "OrgName");
        existingOrg.setVideoMeetingUrl("https://org-video");
        Mockito.when(organisationRepository.findByCvrNumber("12345678")).thenReturn(Optional.of(existingOrg));
        ProviderProfile existingProfWithVideo = new ProviderProfile();
        existingProfWithVideo.setVideoMeetingUrl("https://personal-video");
        Mockito.when(providerProfileRepository.findByUserUuid(any())).thenReturn(Optional.of(existingProfWithVideo));
        cps.onboardTherapist(nullFieldsPayload, nonJwtAuth);

        nullFieldsPayload.put("userUuid", "null");
        existingProfWithVideo.setVideoMeetingUrl("   ");
        cps.onboardTherapist(nullFieldsPayload, null);

        Organisation blankOrg = new Organisation("   ", "   ");
        blankOrg.setAddress("   ");
        blankOrg.setPostalCode("   ");
        blankOrg.setPhoneNumber("   ");
        ProviderProfile blankPp = new ProviderProfile();
        blankPp.setWorkingHoursJson("   ");
        ClientProfile nullFieldsCp = new ClientProfile();
        Assertions.assertEquals(7, cps.getMissingProfileFields(blankPp, blankOrg, nullFieldsCp).size());

        Organisation validOrg = new Organisation("12345678", "Valid Org");
        validOrg.setId(orgId);
        validOrg.setAddress("Addr 1");
        validOrg.setPostalCode("2100");
        validOrg.setPhoneNumber("12345678");
        validOrg.setIban("DK5000400440116243");
        ProviderProfile validPp = new ProviderProfile();
        validPp.setId(providerId);
        validPp.setUserUuid(therapistUuid);
        validPp.setWorkingHoursJson("08:00-16:00");
        validPp.setOrganisation(validOrg);
        Mockito.when(providerProfileRepository.findByUserUuid(therapistUuid)).thenReturn(Optional.of(validPp));
        Mockito.when(providerProfileRepository.findById(providerId)).thenReturn(Optional.of(validPp));

        Map<String, Object> createFromJwt = new HashMap<>();
        createFromJwt.put("name", "JWT Service");
        createFromJwt.put("therapistUuid", "   ");
        createFromJwt.put("userUuid", "   ");
        createFromJwt.put("providerProfileId", "   ");
        createFromJwt.put("organisationId", "   ");
        cps.createService(createFromJwt, tAuth);

        ProviderProfile ppNoUserUuid = new ProviderProfile();
        ppNoUserUuid.setId(providerId);
        ppNoUserUuid.setUserUuid(null);
        ppNoUserUuid.setWorkingHoursJson("08:00-16:00");
        ppNoUserUuid.setOrganisation(validOrg);
        Mockito.when(providerProfileRepository.findById(providerId)).thenReturn(Optional.of(ppNoUserUuid));
        Map<String, Object> createViaProviderId = new HashMap<>();
        createViaProviderId.put("name", "ProviderId Service");
        createViaProviderId.put("providerProfileId", providerId.toString());
        cps.createService(createViaProviderId, null);

        Service existingSvc = new Service();
        existingSvc.setId(serviceId);
        existingSvc.setProvider(ppNoUserUuid);
        existingSvc.setOrganisation(validOrg);
        Mockito.when(serviceRepository.findById(serviceId)).thenReturn(Optional.of(existingSvc));
        Map<String, Object> updMap = new HashMap<>();
        updMap.put("therapistUuid", "   ");
        updMap.put("userUuid", "   ");
        updMap.put("providerProfileId", "   ");
        updMap.put("organisationId", "   ");
        updMap.put("isActive", null);
        cps.updateService(serviceId, updMap, null);

        Service nullFieldsSvc = new Service();
        UUID nullFieldsSvcId = UUID.randomUUID();
        nullFieldsSvc.setId(nullFieldsSvcId);
        Service otherNullSvc = new Service();
        otherNullSvc.setId(UUID.randomUUID());
        otherNullSvc.setName("Other");
        Mockito.when(serviceRepository.findById(nullFieldsSvcId)).thenReturn(Optional.of(nullFieldsSvc));
        Mockito.when(serviceRepository.findAll()).thenReturn(List.of(nullFieldsSvc, otherNullSvc));
        cps.deleteService(nullFieldsSvcId);

        Mockito.when(providerProfileRepository.findAll()).thenReturn(List.of(pp1, pp2));
        Mockito.when(serviceRepository.findAll()).thenReturn(List.of(s1, s2));

        Appointment aptNullLoc = new Appointment(clientUuid, therapistUuid, LocalDateTime.now(), LocalDateTime.now().plusHours(1), "Null Loc", "SCHEDULED");
        Appointment aptVideoLower = new Appointment(clientUuid, therapistUuid, LocalDateTime.now(), LocalDateTime.now().plusHours(1), "Video Lower", "SCHEDULED");
        aptVideoLower.setLocation("video session");
        Appointment aptOnlineNullVideo = new Appointment(clientUuid, UUID.randomUUID(), LocalDateTime.now(), LocalDateTime.now().plusHours(1), "Online Null Video", "SCHEDULED");
        aptOnlineNullVideo.setLocation("online session");
        Mockito.when(aptRepo.findByClientUuid(clientUuid)).thenReturn(List.of(aptNullLoc, aptVideoLower, aptOnlineNullVideo));
        cps.getClientOverview(clientUuid);

        UUID unmappedTherapist = UUID.randomUUID();
        UUID unmappedClient = UUID.randomUUID();
        Mockito.when(providerProfileRepository.findByUserUuid(unmappedTherapist)).thenReturn(Optional.empty());
        Appointment aptUnmappedKlient = new Appointment(unmappedClient, unmappedTherapist, LocalDateTime.now(), LocalDateTime.now().plusHours(1), "Klient", "SCHEDULED");
        aptUnmappedKlient.setLocation("Hos klient");
        Appointment aptUnmappedOnline = new Appointment(unmappedClient, unmappedTherapist, LocalDateTime.now(), LocalDateTime.now().plusHours(1), "Online", "SCHEDULED");
        aptUnmappedOnline.setLocation("online");
        Appointment aptUnmappedVideo = new Appointment(unmappedClient, unmappedTherapist, LocalDateTime.now(), LocalDateTime.now().plusHours(1), "Video", "SCHEDULED");
        aptUnmappedVideo.setLocation("video");
        Appointment aptUnmappedNullLoc = new Appointment(unmappedClient, unmappedTherapist, LocalDateTime.now(), LocalDateTime.now().plusHours(1), "NullLoc", "SCHEDULED");
        aptUnmappedNullLoc.setLocation(null);
        ClientProfile cpNullHome = new ClientProfile(clientUuid, "Name", "1990-01-01", null, null, "123", "a@b.dk");
        ClientProfile cpNullPostal = new ClientProfile(therapistUuid, "Name2", "1990-01-01", "Street 1", null, "123", "a@b.dk");
        Appointment aptNullHomeKlient = new Appointment(clientUuid, unmappedTherapist, LocalDateTime.now(), LocalDateTime.now().plusHours(1), "NullHome", "SCHEDULED");
        aptNullHomeKlient.setLocation("klient");
        Appointment aptNullPostalKlient = new Appointment(therapistUuid, unmappedTherapist, LocalDateTime.now(), LocalDateTime.now().plusHours(1), "NullPostal", "SCHEDULED");
        aptNullPostalKlient.setLocation("klient");
        Mockito.when(clientProfileRepository.findAll()).thenReturn(List.of(cpNullHome, cpNullPostal));
        Mockito.when(aptRepo.findByTherapistUuid(unmappedTherapist)).thenReturn(
                List.of(aptUnmappedKlient, aptUnmappedOnline, aptUnmappedVideo, aptUnmappedNullLoc, aptNullHomeKlient, aptNullPostalKlient)
        );
        Mockito.when(handshakeRepository.findByTherapistUuid(unmappedTherapist)).thenReturn(List.of());
        cps.getTherapistOverview(unmappedTherapist);

        ProviderProfile otherPp = new ProviderProfile();
        otherPp.setId(UUID.randomUUID());
        Organisation otherOrg = new Organisation("99999999", "Other Org");
        otherOrg.setId(UUID.randomUUID());
        Service diffProvSvc = new Service();
        diffProvSvc.setId(UUID.randomUUID());
        diffProvSvc.setName("Diff Prov");
        diffProvSvc.setProvider(otherPp);
        diffProvSvc.setOrganisation(otherOrg);
        Mockito.when(serviceRepository.findAll()).thenReturn(List.of(diffProvSvc));
        Mockito.when(providerProfileRepository.findByUserUuid(therapistUuid)).thenReturn(Optional.of(validPp));
        cps.getTherapistOverview(therapistUuid);

        Map<String, String> blankUuidOnboard = new HashMap<>();
        blankUuidOnboard.put("cvrNumber", "12345678");
        blankUuidOnboard.put("userUuid", "   ");
        cps.onboardTherapist(blankUuidOnboard, null);

        Organisation nullCvrAndNameOrg = new Organisation(null, null);
        Assertions.assertFalse(cps.getMissingProfileFields(validPp, nullCvrAndNameOrg, cp2).isEmpty());

        ProviderProfile statefulMockPp = Mockito.mock(ProviderProfile.class);
        Mockito.when(statefulMockPp.getId()).thenReturn(providerId);
        Mockito.when(statefulMockPp.getWorkingHoursJson()).thenReturn("08:00-16:00");
        Mockito.when(statefulMockPp.getOrganisation()).thenReturn(validOrg);
        Mockito.when(statefulMockPp.findIban()).thenReturn(Optional.of("DK5000400440116243"));
        Mockito.when(statefulMockPp.getCurrency()).thenReturn("DKK");
        Mockito.when(statefulMockPp.getUserUuid()).thenReturn(null, therapistUuid, null, therapistUuid);
        Mockito.when(providerProfileRepository.findById(providerId)).thenReturn(Optional.of(statefulMockPp));
        Mockito.when(clientProfileRepository.findByUserId(therapistUuid)).thenReturn(Optional.of(cp2));

        Service sNullName = new Service();
        sNullName.setId(UUID.randomUUID());
        sNullName.setName(null);
        Service sDiffName = new Service();
        sDiffName.setId(UUID.randomUUID());
        sDiffName.setName("Different Name");
        Service sSameNameDiffProv = new Service();
        sSameNameDiffProv.setId(UUID.randomUUID());
        sSameNameDiffProv.setName("ProviderId Service");
        sSameNameDiffProv.setProvider(otherPp);
        sSameNameDiffProv.setOrganisation(validOrg);
        Service sSameNameNullProv = new Service();
        sSameNameNullProv.setId(UUID.randomUUID());
        sSameNameNullProv.setName("ProviderId Service");
        sSameNameNullProv.setProvider(null);
        sSameNameNullProv.setOrganisation(validOrg);
        Service sSameNameDiffOrg = new Service();
        sSameNameDiffOrg.setId(UUID.randomUUID());
        sSameNameDiffOrg.setName("ProviderId Service");
        sSameNameDiffOrg.setProvider(validPp);
        sSameNameDiffOrg.setOrganisation(otherOrg);
        Service sSameNameNullOrg = new Service();
        sSameNameNullOrg.setId(UUID.randomUUID());
        sSameNameNullOrg.setName("ProviderId Service");
        sSameNameNullOrg.setProvider(validPp);
        sSameNameNullOrg.setOrganisation(null);
        Service sSingleMatch = new Service();
        sSingleMatch.setId(serviceId);
        sSingleMatch.setName("ProviderId Service");
        sSingleMatch.setProvider(validPp);
        sSingleMatch.setOrganisation(validOrg);
        Mockito.when(serviceRepository.findAll()).thenReturn(
                List.of(sNullName, sDiffName, sSameNameDiffProv, sSameNameNullProv, sSameNameDiffOrg, sSameNameNullOrg, sSingleMatch)
        );

        cps.createService(createViaProviderId, nonJwtAuth);

        Map<String, Object> createBothIds = new HashMap<>();
        createBothIds.put("name", "BothIds Service");
        createBothIds.put("therapistUuid", therapistUuid.toString());
        createBothIds.put("providerProfileId", providerId.toString());
        Mockito.when(providerProfileRepository.findById(providerId)).thenReturn(Optional.of(validPp));
        cps.createService(createBothIds, null);

        UUID unknownProvId = UUID.randomUUID();
        Mockito.when(providerProfileRepository.findById(unknownProvId)).thenReturn(Optional.empty());
        createBothIds.put("providerProfileId", unknownProvId.toString());
        cps.createService(createBothIds, null);

        ProviderProfile ppNoOrg = new ProviderProfile();
        ppNoOrg.setId(providerId);
        ppNoOrg.setUserUuid(therapistUuid);
        ppNoOrg.setWorkingHoursJson("08:00-16:00");
        ppNoOrg.setOrganisation(null);
        Mockito.when(providerProfileRepository.findById(providerId)).thenReturn(Optional.of(ppNoOrg));
        Assertions.assertThrows(DomainException.class, () -> cps.createService(createViaProviderId, null));

        ClinicPlatformService lenientCps = new ClinicPlatformService(
                clientProfileRepository, organisationRepository, providerProfileRepository,
                serviceRepository, handshakeRepository, aptRepo, jeRepo, pclRepo
        ) {
            @Override
            public List<String> getMissingProfileFields(ProviderProfile p, Organisation o, ClientProfile c) {
                return List.of();
            }
        };
        Mockito.when(providerProfileRepository.findById(unknownProvId)).thenReturn(Optional.empty());
        Map<String, Object> nullProvOrgPayload = new HashMap<>();
        nullProvOrgPayload.put("name", "ProviderId Service");
        nullProvOrgPayload.put("providerProfileId", unknownProvId.toString());
        lenientCps.createService(nullProvOrgPayload, null);

        Mockito.when(providerProfileRepository.findById(providerId)).thenReturn(Optional.of(statefulMockPp));
        existingSvc.setProvider(statefulMockPp);
        existingSvc.setOrganisation(validOrg);
        Map<String, Object> updNullIds = new HashMap<>();
        updNullIds.put("providerProfileId", null);
        updNullIds.put("organisationId", null);
        cps.updateService(serviceId, updNullIds, nonJwtAuth);

        existingSvc.setProvider(ppNoOrg);
        existingSvc.setOrganisation(null);
        Assertions.assertThrows(DomainException.class, () -> cps.updateService(serviceId, Map.of(), null));

        Mockito.when(serviceRepository.findById(serviceId)).thenReturn(Optional.of(sSingleMatch));
        cps.deleteService(serviceId);
        sSingleMatch.setIsActive(false);
        Assertions.assertTrue(cps.toggleServiceActive(serviceId).getIsActive());

        ClientTherapistHandshake hsNullUuids = new ClientTherapistHandshake(null, null, null, null, null, "REVOKED");
        Mockito.when(handshakeRepository.findByClientUuidAndTherapistUuid(null, null)).thenReturn(Optional.empty());
        cps.createOrUpdateHandshake(hsNullUuids);

        Mockito.when(providerProfileRepository.findByUserUuid(therapistUuid)).thenReturn(Optional.of(ppNoOrg));
        ClientTherapistHandshake hsRevokedReq = new ClientTherapistHandshake(clientUuid, null, therapistUuid, null, null, "REVOKED");
        ClientTherapistHandshake hsExistingWithFields = new ClientTherapistHandshake(clientUuid, "Anders", therapistUuid, "Dr. Jensen", "Org", "ACTIVE");
        Mockito.when(handshakeRepository.findByClientUuidAndTherapistUuid(clientUuid, therapistUuid)).thenReturn(Optional.of(hsExistingWithFields));
        cps.createOrUpdateHandshake(hsRevokedReq);

        UUID hsId = UUID.randomUUID();
        Mockito.when(handshakeRepository.findById(hsId)).thenReturn(Optional.of(hsExistingWithFields));
        Map<String, String> hsNullValues = new HashMap<>();
        hsNullValues.put("status", null);
        hsNullValues.put("validFrom", null);
        hsNullValues.put("validTo", null);
        hsNullValues.put("historyStartDate", null);
        hsNullValues.put("historyEndDate", null);
        cps.updateHandshakeStatus(hsId, hsNullValues);
        cps.updateHandshakeStatus(hsId, Map.of("status", "   "));
        cps.updateHandshakeStatus(hsId, Map.of("status", "PENDING"));

        Jwt noSubJwt = Jwt.withTokenValue("tok")
                .header("alg", "none")
                .claim("iss", "test")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(3600))
                .build();
        Authentication noSubAuth = Mockito.mock(Authentication.class);
        Mockito.when(noSubAuth.getPrincipal()).thenReturn(noSubJwt);
        Assertions.assertThrows(DomainException.class, () -> cps.getCurrentUserContext(noSubAuth));

        Organisation orgBlankVideo = new Organisation("12345678", "Org");
        orgBlankVideo.setVideoMeetingUrl("   ");
        ProviderProfile ppBlankVideo = new ProviderProfile();
        ppBlankVideo.setId(providerId);
        ppBlankVideo.setUserUuid(therapistUuid);
        ppBlankVideo.setVideoMeetingUrl("   ");
        ppBlankVideo.setOrganisation(orgBlankVideo);
        Mockito.when(providerProfileRepository.findAll()).thenReturn(List.of(ppBlankVideo));
        Mockito.when(providerProfileRepository.findByUserUuid(therapistUuid)).thenReturn(Optional.of(ppBlankVideo));
        Appointment aptOnlineBlankVideo = new Appointment(clientUuid, therapistUuid, LocalDateTime.now(), LocalDateTime.now().plusHours(1), "Online", "SCHEDULED");
        aptOnlineBlankVideo.setLocation("online");
        Appointment aptRegularLoc = new Appointment(clientUuid, therapistUuid, LocalDateTime.now(), LocalDateTime.now().plusHours(1), "Office", "SCHEDULED");
        aptRegularLoc.setLocation("Kontor");
        Mockito.when(aptRepo.findByClientUuid(clientUuid)).thenReturn(List.of(aptOnlineBlankVideo));
        cps.getClientOverview(clientUuid);

        ClientProfile cpBlankHome = new ClientProfile(clientUuid, "Name", "1990-01-01", "   ", "2100", "123", "a@b.dk");
        Mockito.when(clientProfileRepository.findAll()).thenReturn(List.of(cpBlankHome));
        Appointment aptKlientBlankHome = new Appointment(clientUuid, therapistUuid, LocalDateTime.now(), LocalDateTime.now().plusHours(1), "Klient", "SCHEDULED");
        aptKlientBlankHome.setLocation("klient");
        Mockito.when(aptRepo.findByTherapistUuid(therapistUuid)).thenReturn(List.of(aptOnlineBlankVideo, aptRegularLoc, aptKlientBlankHome));
        cps.getTherapistOverview(therapistUuid);

        Appointment aptHjemmebesog = new Appointment(clientUuid, therapistUuid, LocalDateTime.now(), LocalDateTime.now().plusHours(1), "Hjemme", "SCHEDULED");
        aptHjemmebesog.setLocation("hjemmebesøg");
        Mockito.when(aptRepo.findByTherapistUuid(therapistUuid)).thenReturn(List.of(aptHjemmebesog));
        cps.getTherapistOverview(therapistUuid);

        Service svcNoProvNoOrg = new Service();
        svcNoProvNoOrg.setId(serviceId);
        svcNoProvNoOrg.setProvider(null);
        svcNoProvNoOrg.setOrganisation(null);
        Mockito.when(serviceRepository.findById(serviceId)).thenReturn(Optional.of(svcNoProvNoOrg));
        Mockito.when(providerProfileRepository.findByUserUuid(therapistUuid)).thenReturn(Optional.of(validPp));
        cps.updateService(serviceId, Map.of(), tAuth);

        svcNoProvNoOrg.setProvider(null);
        svcNoProvNoOrg.setOrganisation(null);
        Assertions.assertThrows(DomainException.class, () -> cps.updateService(serviceId, Map.of(), null));
        Assertions.assertThrows(DomainException.class, () -> cps.updateService(serviceId, Map.of(), nonJwtAuth));

        Jwt nonListRolesJwt = Jwt.withTokenValue("tok3")
                .header("alg", "none")
                .claim("sub", clientUuid.toString())
                .claim("realm_access", Map.of("roles", "invalid"))
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(3600))
                .build();
        Authentication nonListRolesAuth = Mockito.mock(Authentication.class);
        Mockito.when(nonListRolesAuth.getPrincipal()).thenReturn(nonListRolesJwt);
        Mockito.when(clientProfileRepository.findByUserId(clientUuid)).thenReturn(Optional.of(cp2));
        cps.getCurrentUserContext(nonListRolesAuth);

        Mockito.when(serviceRepository.findById(serviceId)).thenReturn(Optional.of(sSingleMatch));
        controller.updateService(serviceId, Map.of("name", "Updated 2-arg", "therapistUuid", therapistUuid.toString()));

        pp1.setUser(new UserEntity());
        Assertions.assertNotNull(pp1.getUser());
        validOrg.setCvrNumber("87654321");
        Assertions.assertNull(cp1.getId());
        Assertions.assertNull(s1.getDescription());
        sSingleMatch.setIsActive(null);
        Assertions.assertTrue(sSingleMatch.getIsActive());

        // Currency & IBAN & Handshake restrictions coverage
        Organisation currOrg = new Organisation("11223344", "Currency Clinic");
        currOrg.setCurrency(null);
        Assertions.assertEquals("DKK", currOrg.getCurrency());
        currOrg.setCurrency("   ");
        Assertions.assertEquals("DKK", currOrg.getCurrency());
        currOrg.setCurrency("eur");
        Assertions.assertEquals("EUR", currOrg.getCurrency());
        Assertions.assertEquals(Currency.getInstance("EUR"), currOrg.resolveJavaCurrency());
        ReflectionTestUtils.setField(currOrg, "currency", null);
        Assertions.assertEquals("DKK", currOrg.getCurrency());
        ReflectionTestUtils.setField(currOrg, "currency", "   ");
        Assertions.assertEquals("DKK", currOrg.getCurrency());

        ProviderProfile currPp = new ProviderProfile();
        currPp.setCurrency(null);
        Assertions.assertEquals("DKK", currPp.getCurrency());
        currPp.setCurrency("   ");
        Assertions.assertEquals("DKK", currPp.getCurrency());
        currPp.setCurrency("usd");
        Assertions.assertEquals("USD", currPp.getCurrency());
        Assertions.assertEquals(Currency.getInstance("USD"), currPp.resolveJavaCurrency());
        ReflectionTestUtils.setField(currPp, "currency", null);
        Assertions.assertEquals("DKK", currPp.getCurrency());
        ReflectionTestUtils.setField(currPp, "currency", "   ");
        Assertions.assertEquals("DKK", currPp.getCurrency());

        Service currSvc = new Service();
        currSvc.setCurrency(null);
        Assertions.assertEquals("DKK", currSvc.getCurrency());
        currSvc.setCurrency("   ");
        Assertions.assertEquals("DKK", currSvc.getCurrency());
        currSvc.setCurrency("gbp");
        Assertions.assertEquals("GBP", currSvc.getCurrency());
        Assertions.assertEquals(Currency.getInstance("GBP"), currSvc.resolveJavaCurrency());
        ReflectionTestUtils.setField(currSvc, "currency", null);
        Assertions.assertEquals("DKK", currSvc.getCurrency());
        ReflectionTestUtils.setField(currSvc, "currency", "   ");
        Assertions.assertEquals("DKK", currSvc.getCurrency());

        ClientTherapistHandshake branchHs = new ClientTherapistHandshake();
        branchHs.setAllowedBranches(null);
        Assertions.assertTrue(branchHs.getAllowedBranchesList().isEmpty());
        Assertions.assertTrue(branchHs.isBranchAllowed("any-branch"));
        branchHs.setAllowedBranches("   ");
        Assertions.assertTrue(branchHs.getAllowedBranchesList().isEmpty());
        branchHs.setAllowedBranches(" , branch-1 , , branch-2 ");
        Assertions.assertEquals(List.of("branch-1", "branch-2"), branchHs.getAllowedBranchesList());
        Assertions.assertTrue(branchHs.isBranchAllowed("branch-1"));
        Assertions.assertFalse(branchHs.isBranchAllowed("main"));
        Assertions.assertFalse(branchHs.isBranchAllowed(null));
        Assertions.assertFalse(branchHs.isBranchAllowed("   "));

        branchHs.setCustomRestrictions(null);
        Assertions.assertTrue(branchHs.getCustomRestrictionsList().isEmpty());
        Assertions.assertFalse(branchHs.hasCustomRestriction(null));
        Assertions.assertFalse(branchHs.hasCustomRestriction("   "));
        branchHs.setCustomRestrictions("   ");
        Assertions.assertTrue(branchHs.getCustomRestrictionsList().isEmpty());
        branchHs.setCustomRestrictions(" , NO_MOOD_SCORES , , NO_PCL5_SCORES ");
        Assertions.assertEquals(List.of("NO_MOOD_SCORES", "NO_PCL5_SCORES"), branchHs.getCustomRestrictionsList());
        Assertions.assertTrue(branchHs.hasCustomRestriction("NO_MOOD_SCORES"));
        Assertions.assertFalse(branchHs.hasCustomRestriction("OTHER_FLAG"));

        Map<String, String> onboardIban = new HashMap<>();
        onboardIban.put("cvrNumber", "12345678");
        onboardIban.put("iban", "DK5000400440116243");
        onboardIban.put("currency", "EUR");
        cps.onboardTherapist(onboardIban, null);

        Map<String, String> onboardNullIbanCurr = new HashMap<>();
        onboardNullIbanCurr.put("cvrNumber", "12345678");
        onboardNullIbanCurr.put("iban", null);
        onboardNullIbanCurr.put("currency", null);
        ProviderProfile existingProfWithIban = new ProviderProfile();
        existingProfWithIban.setIban("DK1122334455667788");
        existingProfWithIban.setCurrency("SEK");
        Mockito.when(providerProfileRepository.findByUserUuid(any())).thenReturn(Optional.of(existingProfWithIban));
        cps.onboardTherapist(onboardNullIbanCurr, null);

        Map<String, String> onboardBlankCurr = new HashMap<>();
        onboardBlankCurr.put("cvrNumber", "12345678");
        onboardBlankCurr.put("currency", "   ");
        ReflectionTestUtils.setField(existingProfWithIban, "iban", null);
        ReflectionTestUtils.setField(existingProfWithIban, "currency", null);
        cps.onboardTherapist(onboardBlankCurr, null);

        Map<String, String> onboardInvalidCurr = new HashMap<>();
        onboardInvalidCurr.put("cvrNumber", "12345678");
        onboardInvalidCurr.put("currency", "INVALID_CURR");
        Assertions.assertThrows(DomainException.class, () -> cps.onboardTherapist(onboardInvalidCurr, null));

        ProviderProfile ppWithIban = new ProviderProfile();
        ppWithIban.setIban("DK5000400440116243");
        ppWithIban.setWorkingHoursJson("09-16");
        Assertions.assertFalse(cps.getMissingProfileFields(ppWithIban, validOrg, cp2).contains("IBAN"));

        ProviderProfile ppBlankIban = new ProviderProfile();
        ppBlankIban.setIban("   ");
        Organisation orgNullIban = new Organisation("12345678", "Org");
        orgNullIban.setIban(null);
        Assertions.assertTrue(cps.getMissingProfileFields(ppBlankIban, orgNullIban, cp2).contains("IBAN"));

        Map<String, Object> createBlankCurr = new HashMap<>(createViaProviderId);
        createBlankCurr.put("currency", "   ");
        Mockito.when(providerProfileRepository.findById(providerId)).thenReturn(Optional.of(validPp));
        cps.createService(createBlankCurr, null);

        Map<String, Object> createOrgOnlyCurr = new HashMap<>();
        createOrgOnlyCurr.put("name", "OrgOnly Service");
        createOrgOnlyCurr.put("organisationId", orgId.toString());
        Mockito.when(organisationRepository.findById(orgId)).thenReturn(Optional.of(validOrg));
        lenientCps.createService(createOrgOnlyCurr, null);

        Map<String, Object> createInvalidCurr = new HashMap<>(createViaProviderId);
        createInvalidCurr.put("currency", "INVALID_CURR");
        Mockito.when(providerProfileRepository.findById(providerId)).thenReturn(Optional.of(validPp));
        Assertions.assertThrows(DomainException.class, () -> cps.createService(createInvalidCurr, null));

        Mockito.when(serviceRepository.findById(serviceId)).thenReturn(Optional.of(sSingleMatch));
        Assertions.assertThrows(
                DomainException.class,
                () -> cps.updateService(serviceId, Map.of("currency", "INVALID_CURR", "therapistUuid", therapistUuid.toString()), null)
        );
        cps.updateService(serviceId, Map.of("currency", "EUR", "therapistUuid", therapistUuid.toString()), null);
        Map<String, Object> updNullCurr = new HashMap<>();
        updNullCurr.put("currency", null);
        updNullCurr.put("therapistUuid", therapistUuid.toString());
        cps.updateService(serviceId, updNullCurr, null);

        ClientTherapistHandshake hsWithBranch = new ClientTherapistHandshake(clientUuid, "Anders", therapistUuid, "Dr", "Org", "ACTIVE");
        hsWithBranch.setAllowedBranches("branch-1");
        hsWithBranch.setCustomRestrictions("NO_MOOD_SCORES,NO_PCL5_SCORES");
        Mockito.when(handshakeRepository.findByClientUuidAndTherapistUuid(clientUuid, therapistUuid)).thenReturn(Optional.of(hsExistingWithFields));
        cps.createOrUpdateHandshake(hsWithBranch);

        Map<String, String> hsBranchPatch = new HashMap<>();
        hsBranchPatch.put("allowedBranches", "branch-1");
        hsBranchPatch.put("customRestrictions", "NO_MOOD_SCORES");
        cps.updateHandshakeStatus(hsId, hsBranchPatch);

        Map<String, String> hsBranchBlankPatch = new HashMap<>();
        hsBranchBlankPatch.put("allowedBranches", "   ");
        hsBranchBlankPatch.put("customRestrictions", "   ");
        cps.updateHandshakeStatus(hsId, hsBranchBlankPatch);

        Map<String, String> hsBranchNullPatch = new HashMap<>();
        hsBranchNullPatch.put("allowedBranches", null);
        hsBranchNullPatch.put("customRestrictions", null);
        cps.updateHandshakeStatus(hsId, hsBranchNullPatch);

        hsExistingWithFields.setStatus("ACTIVE");
        hsExistingWithFields.setAllowedBranches("branch-1");
        hsExistingWithFields.setCustomRestrictions("NO_MOOD_SCORES,NO_PCL5_SCORES");
        JournalEntry entryB1 = new JournalEntry("T", "C", 8, LocalDateTime.now(), clientUuid);
        entryB1.setGitBranch("branch-1");
        Mockito.when(jeRepo.findByClientUuidOrderByTimestampDesc(clientUuid)).thenReturn(List.of(entryB1));
        Mockito.when(handshakeRepository.findByTherapistUuid(therapistUuid)).thenReturn(List.of(hsExistingWithFields));
        cps.getTherapistOverview(therapistUuid);

        // Also test when NO_MOOD_SCORES is NOT active so latestMood is populated
        hsExistingWithFields.setCustomRestrictions("");
        cps.getTherapistOverview(therapistUuid);

        ClientTherapistHandshake hsBranchMatrix = new ClientTherapistHandshake();
        Assertions.assertTrue(hsBranchMatrix.getBranchRestrictionsMap().isEmpty());
        hsBranchMatrix.setBranchRestrictions("   ");
        Assertions.assertTrue(hsBranchMatrix.getBranchRestrictionsMap().isEmpty());
        hsBranchMatrix.setBranchRestrictions(" ; no-colon ; :empty-branch ;   :blank-branch ; branch-empty: , ; branch-a:NO_MOOD_SCORES,READ_ONLY_TIMELINE ; branch-b:NO_CLINICAL_NOTES_EXPORT ");
        Map<String, List<String>> parsedBrMap = hsBranchMatrix.getBranchRestrictionsMap();
        Assertions.assertEquals(2, parsedBrMap.size());
        Assertions.assertEquals(List.of("NO_MOOD_SCORES", "READ_ONLY_TIMELINE"), hsBranchMatrix.getRestrictionsForBranch("branch-a"));
        Assertions.assertEquals(List.of("NO_CLINICAL_NOTES_EXPORT"), hsBranchMatrix.getRestrictionsForBranch("branch-b"));
        Assertions.assertTrue(hsBranchMatrix.getRestrictionsForBranch("unknown-branch").isEmpty());
        Assertions.assertTrue(hsBranchMatrix.getRestrictionsForBranch(null).isEmpty());
        Assertions.assertTrue(hsBranchMatrix.getRestrictionsForBranch("   ").isEmpty());

        Assertions.assertTrue(hsBranchMatrix.hasRestrictionForBranch("branch-a", "NO_MOOD_SCORES"));
        Assertions.assertFalse(hsBranchMatrix.hasRestrictionForBranch("branch-b", "NO_MOOD_SCORES"));
        Assertions.assertFalse(hsBranchMatrix.hasRestrictionForBranch("branch-a", null));
        Assertions.assertFalse(hsBranchMatrix.hasRestrictionForBranch("branch-a", "   "));
        Assertions.assertTrue(hsBranchMatrix.hasCustomRestriction("NO_MOOD_SCORES"));
        Assertions.assertTrue(hsBranchMatrix.hasCustomRestriction("NO_CLINICAL_NOTES_EXPORT"));
        Assertions.assertFalse(hsBranchMatrix.hasCustomRestriction("NON_EXISTENT_FLAG"));

        ClientTherapistHandshake hsGlobalOnly = new ClientTherapistHandshake();
        hsGlobalOnly.setCustomRestrictions("NO_MOOD_SCORES");
        Assertions.assertTrue(hsGlobalOnly.hasRestrictionForBranch("any-branch", "NO_MOOD_SCORES"));
        Assertions.assertFalse(hsGlobalOnly.hasRestrictionForBranch("any-branch", "OTHER"));

        hsBranchMatrix.setAllowedBranches("branch-a,branch-c");
        List<String> combinedBranches = hsBranchMatrix.getAllowedBranchesList();
        Assertions.assertEquals(List.of("branch-a", "branch-c", "branch-b"), combinedBranches);

        hsWithBranch.setBranchRestrictions("branch-1:NO_MOOD_SCORES");
        cps.createOrUpdateHandshake(hsWithBranch);

        Map<String, String> hsBrMatrixPatch = new HashMap<>();
        hsBrMatrixPatch.put("branchRestrictions", "branch-1:NO_MOOD_SCORES;branch-2:READ_ONLY_TIMELINE");
        cps.updateHandshakeStatus(hsId, hsBrMatrixPatch);

        Map<String, String> hsBrBlankPatch = new HashMap<>();
        hsBrBlankPatch.put("branchRestrictions", "   ");
        cps.updateHandshakeStatus(hsId, hsBrBlankPatch);

        Map<String, String> hsBrNullPatch = new HashMap<>();
        hsBrNullPatch.put("branchRestrictions", null);
        cps.updateHandshakeStatus(hsId, hsBrNullPatch);

        ClientTherapistHandshake hsOptTest = new ClientTherapistHandshake(clientUuid, "Anders", therapistUuid, "Bo", "Nordic", "ACTIVE");
        hsOptTest.setRevokedAt(LocalDateTime.now());
        hsOptTest.setValidFrom(LocalDate.now().minusDays(1));
        hsOptTest.setValidTo(LocalDate.now().plusDays(1));
        hsOptTest.setHistoryStartDate(LocalDate.now().minusDays(10));
        hsOptTest.setHistoryEndDate(LocalDate.now().plusDays(10));
        hsOptTest.setAllowedBranches("main");
        hsOptTest.setCustomRestrictions("NO_MOOD_SCORES");
        hsOptTest.setBranchRestrictions("main:NO_MOOD_SCORES");
        Assertions.assertTrue(hsOptTest.findClientName().isPresent());
        Assertions.assertTrue(hsOptTest.findTherapistName().isPresent());
        Assertions.assertTrue(hsOptTest.findOrganisationName().isPresent());
        Assertions.assertTrue(hsOptTest.findGrantedAt().isPresent());
        Assertions.assertTrue(hsOptTest.findRevokedAt().isPresent());
        Assertions.assertTrue(hsOptTest.findValidFrom().isPresent());
        Assertions.assertTrue(hsOptTest.findValidTo().isPresent());
        Assertions.assertTrue(hsOptTest.findHistoryStartDate().isPresent());
        Assertions.assertTrue(hsOptTest.findHistoryEndDate().isPresent());
        Assertions.assertTrue(hsOptTest.findAllowedBranches().isPresent());
        Assertions.assertTrue(hsOptTest.findCustomRestrictions().isPresent());
        Assertions.assertTrue(hsOptTest.findBranchRestrictions().isPresent());

        ClientTherapistHandshake hsOptEmpty = new ClientTherapistHandshake();
        hsOptEmpty.setClientName("   ");
        hsOptEmpty.setTherapistName("   ");
        hsOptEmpty.setOrganisationName("   ");
        Assertions.assertTrue(hsOptEmpty.findClientName().isEmpty());
        Assertions.assertTrue(hsOptEmpty.findTherapistName().isEmpty());
        Assertions.assertTrue(hsOptEmpty.findOrganisationName().isEmpty());
        Assertions.assertTrue(hsOptEmpty.findGrantedAt().isEmpty());
        Assertions.assertTrue(hsOptEmpty.findRevokedAt().isEmpty());
        Assertions.assertTrue(hsOptEmpty.findValidFrom().isEmpty());
        Assertions.assertTrue(hsOptEmpty.findValidTo().isEmpty());
        Assertions.assertTrue(hsOptEmpty.findHistoryStartDate().isEmpty());
        Assertions.assertTrue(hsOptEmpty.findHistoryEndDate().isEmpty());

        Assertions.assertTrue(validOrg.findIban().isPresent());
        Organisation blankIbanOrg = new Organisation("99999999", "Blank IBAN Org");
        blankIbanOrg.setIban("   ");
        Assertions.assertTrue(blankIbanOrg.findIban().isEmpty());
        Assertions.assertTrue(validPp.findOrganisation().isPresent());
        Assertions.assertTrue(sSingleMatch.findProvider().isPresent());
        Assertions.assertTrue(sSingleMatch.findOrganisation().isPresent());

        // PlatformEventService & PlatformCacheService
        PlatformEventService eventService = new PlatformEventService();
        eventService.clearEvents();
        eventService.publishEvent("clinic-events", "CREATE", "id-1", Map.of("k", "v"));
        Assertions.assertFalse(eventService.getPublishedEvents().isEmpty());
        Assertions.assertTrue(eventService.hasEvent("clinic-events", "CREATE"));
        Assertions.assertFalse(eventService.hasEvent("clinic-events", "DELETE"));
        Assertions.assertFalse(eventService.hasEvent("other-topic", "CREATE"));
        eventService.clearEvents();
        Assertions.assertTrue(eventService.getPublishedEvents().isEmpty());

        PlatformCacheService customCache = new PlatformCacheService();
        Assertions.assertTrue(customCache.isConnected());
        customCache.put("k1", "v1");
        Assertions.assertTrue(customCache.containsKey("k1"));
        Assertions.assertFalse(customCache.containsKey("k2"));
        Assertions.assertNotNull(customCache.get("k1"));
        Assertions.assertNull(customCache.get("k2"));
        Assertions.assertTrue(customCache.getCacheHitCount() >= 1);
        customCache.evict("k1");
        customCache.clearAll();

        // Exercise all Java Record DTOs in ClinicPlatformFeature
        OrganisationDto orgDto = OrganisationDto.fromEntity(validOrg);
        Assertions.assertEquals(validOrg.getId(), orgDto.id());
        ProviderProfileDto ppDtoWithOrg = ProviderProfileDto.fromEntity(validPp);
        Assertions.assertEquals(validPp.getId(), ppDtoWithOrg.id());
        ProviderProfile ppNoOrg2 = new ProviderProfile();
        ppNoOrg2.setId(UUID.randomUUID());
        Assertions.assertNull(ProviderProfileDto.fromEntity(ppNoOrg2).organisationId());
        ServiceDto svcDtoWithRefs = ServiceDto.fromEntity(sSingleMatch);
        Assertions.assertEquals(sSingleMatch.getId(), svcDtoWithRefs.id());
        Service svcNoRefs = new Service();
        svcNoRefs.setId(UUID.randomUUID());
        Assertions.assertNull(ServiceDto.fromEntity(svcNoRefs).providerProfileId());
        ClientTherapistHandshakeDto hsDto = ClientTherapistHandshakeDto.fromEntity(hsOptTest);
        Assertions.assertEquals(hsOptTest.getId(), hsDto.id());
        OnboardingRequestDto onboardReqDto = new OnboardingRequestDto(UUID.randomUUID(), UUID.randomUUID(), "Dr. A", "12345678", "Org", "Bio", "09:00-17:00", "DK5000400440116243", "DKK");
        Assertions.assertEquals("Dr. A", onboardReqDto.displayName());
        HandshakeRequestDto hsReqDto = new HandshakeRequestDto(UUID.randomUUID(), clientUuid, "Anders", UUID.randomUUID(), "Bo", "Org", "ACTIVE");
        Assertions.assertEquals("ACTIVE", hsReqDto.status());
    }

    @Test
    public void testClinicClientsBaseCrudRoutesAndValidation() {
        UUID clientId = UUID.randomUUID();
        ClientProfile existing = new ClientProfile(
                UUID.randomUUID(), "Maja Klient", "1990-01-01", "Vej 1", "1000", "+4511223344", "maja@example.dk"
        );
        existing.setId(clientId);

        Mockito.when(clientProfileRepository.findAll()).thenReturn(List.of(existing));
        Mockito.when(clientProfileRepository.findAll(PageRequest.of(0, 10)))
                .thenReturn(new PageImpl<>(List.of(existing)));
        Mockito.when(clientProfileRepository.findById(clientId)).thenReturn(Optional.of(existing));
        Mockito.when(clientProfileRepository.existsById(clientId)).thenReturn(true);
        Mockito.when(clientProfileRepository.save(any(ClientProfile.class))).thenAnswer(i -> {
            ClientProfile cp = i.getArgument(0);
            if (cp.getId() == null) {
                cp.setId(clientId);
            }
            return cp;
        });

        Assertions.assertEquals(1, controller.getAll().getBody().size());
        Assertions.assertEquals(1, controller.getAllPaged(PageRequest.of(0, 10)).getBody().getTotalElements());
        Assertions.assertEquals("Maja Klient", controller.getById(clientId).getBody().fullName());

        // Partial DTO with null optional fields & validation checks
        Assertions.assertThrows(DomainException.class, () -> controller.create(null));
        Assertions.assertThrows(DomainException.class, () ->
                controller.create(new ClientProfileDto(null, null, null, null, null, null, null, "x@example.dk"))
        );
        Assertions.assertThrows(DomainException.class, () ->
                controller.create(new ClientProfileDto(null, null, "   ", null, null, null, null, "x@example.dk"))
        );

        try {
            RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(new MockHttpServletRequest()));
            ResponseEntity<ClientProfileDto> created = controller.create(
                    new ClientProfileDto(null, null, "Ny Klient", null, null, null, null, "ny@example.dk")
            );
            Assertions.assertEquals(HttpStatus.CREATED, created.getStatusCode());
            Assertions.assertEquals("Ny Klient", created.getBody().fullName());
            Assertions.assertNotNull(created.getBody().userId());
        } finally {
            RequestContextHolder.resetRequestAttributes();
        }

        ResponseEntity<ClientProfileDto> updated = controller.update(
                clientId,
                new ClientProfileDto(clientId, existing.getUserId(), "Opdateret Klient", "1990-01-01", "Vej 2", "2000", "+4599887766", "maja2@example.dk")
        );
        Assertions.assertEquals(HttpStatus.OK, updated.getStatusCode());
        Assertions.assertEquals("Opdateret Klient", updated.getBody().fullName());

        Assertions.assertEquals(HttpStatus.NO_CONTENT, controller.delete(clientId).getStatusCode());
    }

    @Test
    public void testTherapistFilteredServicesAndOverviewSelfExclusion() {
        UUID therapistUuidA = UUID.randomUUID();
        UUID therapistUuidB = UUID.randomUUID();
        UUID unknownTherapistUuid = UUID.randomUUID();

        Organisation sharedOrg = new Organisation("55667788", "Fælles Klinik");
        sharedOrg.setId(UUID.randomUUID());

        ProviderProfile providerA = new ProviderProfile();
        providerA.setId(UUID.randomUUID());
        providerA.setUserUuid(therapistUuidA);
        providerA.setDisplayName("Behandler A");
        providerA.setOrganisation(sharedOrg);

        ProviderProfile providerB = new ProviderProfile();
        providerB.setId(UUID.randomUUID());
        providerB.setUserUuid(therapistUuidB);
        providerB.setDisplayName("Behandler B");
        providerB.setOrganisation(sharedOrg);

        Service activeServiceA = new Service();
        activeServiceA.setId(UUID.randomUUID());
        activeServiceA.setName("Ydelse A Aktiv");
        activeServiceA.setProvider(providerA);
        activeServiceA.setOrganisation(sharedOrg);
        activeServiceA.setIsActive(true);

        Service inactiveServiceA = new Service();
        inactiveServiceA.setId(UUID.randomUUID());
        inactiveServiceA.setName("Ydelse A Inaktiv");
        inactiveServiceA.setProvider(providerA);
        inactiveServiceA.setOrganisation(sharedOrg);
        inactiveServiceA.setIsActive(false);

        Service personalServiceB = new Service();
        personalServiceB.setId(UUID.randomUUID());
        personalServiceB.setName("Ydelse B Personlig");
        personalServiceB.setProvider(providerB);
        personalServiceB.setOrganisation(sharedOrg);
        personalServiceB.setIsActive(true);

        Service orgSharedService = new Service();
        orgSharedService.setId(UUID.randomUUID());
        orgSharedService.setName("Fælles Organisationsydelse");
        orgSharedService.setOrganisation(sharedOrg);
        orgSharedService.setIsActive(true);

        Mockito.when(providerProfileRepository.findByUserUuid(therapistUuidA)).thenReturn(Optional.of(providerA));
        Mockito.when(providerProfileRepository.findByUserUuid(unknownTherapistUuid)).thenReturn(Optional.empty());
        Mockito.when(providerProfileRepository.findAll()).thenReturn(List.of(providerA, providerB));
        Mockito.when(serviceRepository.findAll()).thenReturn(
                List.of(activeServiceA, inactiveServiceA, personalServiceB, orgSharedService)
        );
        Mockito.when(serviceRepository.findById(activeServiceA.getId())).thenReturn(Optional.of(activeServiceA));
        Mockito.doCallRealMethod().when(serviceRepository).findServiceById(any());

        // Active-only filter without therapist filters globally
        Assertions.assertEquals(4, controller.getServices(null, false).getBody().size());
        Assertions.assertEquals(3, controller.getServices(null, true).getBody().size());

        // Therapist A receives personal A + shared org service, NOT Therapist B's personal service
        List<Service> allForA = controller.getServices(therapistUuidA, false).getBody();
        Assertions.assertEquals(3, allForA.size());
        Assertions.assertFalse(allForA.stream().anyMatch(s -> "Ydelse B Personlig".equals(s.getName())));

        List<Service> activeForA = controller.getServices(therapistUuidA, true).getBody();
        Assertions.assertEquals(2, activeForA.size());

        // Unknown therapist returns empty list without falling back to all services
        Assertions.assertTrue(controller.getServices(unknownTherapistUuid, true).getBody().isEmpty());

        // Default port method delegates to findById
        Assertions.assertTrue(serviceRepository.findServiceById(activeServiceA.getId()).isPresent());

        // Overview excludes viewing user from providers list
        Map<String, Object> overviewForA = controller.getClientOverview(therapistUuidA).getBody();
        List<?> visibleProviders = (List<?>) overviewForA.get("providers");
        Assertions.assertEquals(1, visibleProviders.size());
    }
}
