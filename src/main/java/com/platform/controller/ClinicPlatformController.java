package com.platform.controller;

import com.platform.crm.model.JournalEntry;
import com.platform.crm.model.PsychotraumatologyTest;
import com.platform.crm.repository.JournalEntryRepository;
import com.platform.crm.repository.PsychotraumatologyTestRepository;
import com.platform.entity.*;
import com.platform.repository.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1")
@CrossOrigin(origins = "*")
public class ClinicPlatformController {

    private final ClientProfileRepository clientProfileRepository;
    private final OrganisationRepository organisationRepository;
    private final ProviderProfileRepository providerProfileRepository;
    private final ServiceRepository serviceRepository;
    private final HandshakeRepository handshakeRepository;
    private final AppointmentRepository appointmentRepository;
    private final JournalEntryRepository journalEntryRepository;
    private final PsychotraumatologyTestRepository psychotraumatologyTestRepository;

    public ClinicPlatformController(
            ClientProfileRepository clientProfileRepository,
            OrganisationRepository organisationRepository,
            ProviderProfileRepository providerProfileRepository,
            ServiceRepository serviceRepository,
            HandshakeRepository handshakeRepository
    ) {
        this(
                clientProfileRepository,
                organisationRepository,
                providerProfileRepository,
                serviceRepository,
                handshakeRepository,
                null,
                null,
                null
        );
    }

    @Autowired
    public ClinicPlatformController(
            ClientProfileRepository clientProfileRepository,
            OrganisationRepository organisationRepository,
            ProviderProfileRepository providerProfileRepository,
            ServiceRepository serviceRepository,
            HandshakeRepository handshakeRepository,
            AppointmentRepository appointmentRepository,
            JournalEntryRepository journalEntryRepository,
            PsychotraumatologyTestRepository psychotraumatologyTestRepository
    ) {
        this.clientProfileRepository = clientProfileRepository;
        this.organisationRepository = organisationRepository;
        this.providerProfileRepository = providerProfileRepository;
        this.serviceRepository = serviceRepository;
        this.handshakeRepository = handshakeRepository;
        this.appointmentRepository = appointmentRepository;
        this.journalEntryRepository = journalEntryRepository;
        this.psychotraumatologyTestRepository = psychotraumatologyTestRepository;
    }

    // =========================================================================
    // 0. SESSION / IDENTITY BOOTSTRAP & OVERVIEW ENDPOINTS (DB-BACKED)
    // =========================================================================

    @GetMapping("/clinic/me")
    public ResponseEntity<Map<String, Object>> getCurrentUserContext(Authentication auth) {
        Jwt jwt = extractJwt(auth);
        UUID userUuid = extractUserUuid(jwt);
        String username = extractClaim(jwt, "preferred_username", null);
        String fullName = extractClaim(jwt, "name", username);
        String email = extractClaim(jwt, "email", null);
        List<String> roles = extractRoles(jwt);

        ClientProfile clientProfile = clientProfileRepository.findByUserId(userUuid).orElse(null);
        ProviderProfile providerProfile = providerProfileRepository.findByUserUuid(userUuid).orElse(null);
        boolean isTherapist = roles.stream().anyMatch(r -> r.equalsIgnoreCase("therapist") || r.equalsIgnoreCase("ROLE_therapist"));
        boolean isAdmin = roles.stream().anyMatch(r -> r.equalsIgnoreCase("admin") || r.equalsIgnoreCase("ROLE_admin"));

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("userUuid", userUuid);
        result.put("username", username);
        result.put("fullName", clientProfile != null ? Objects.requireNonNullElse(clientProfile.getFullName(), fullName) : fullName);
        result.put("email", clientProfile != null ? Objects.requireNonNullElse(clientProfile.getEmail(), email) : email);
        result.put("roles", roles);
        result.put("isTherapist", isTherapist);
        result.put("isAdmin", isAdmin);
        result.put("clientProfile", clientProfile);
        result.put("providerProfile", providerProfile);
        return ResponseEntity.ok(result);
    }

    @GetMapping("/clinic/directory")
    public ResponseEntity<Map<String, Object>> getMarketplaceDirectory() {
        List<Organisation> organisations = organisationRepository.findAll();
        List<ProviderProfile> providers = providerProfileRepository.findAll();
        List<Service> services = serviceRepository.findAll();

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("organisations", organisations);
        response.put("providers", providers);
        response.put("services", services);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/clinic/client/{clientUuid}/overview")
    public ResponseEntity<Map<String, Object>> getClientOverview(@PathVariable UUID clientUuid) {
        ClientProfile clientProfile = clientProfileRepository.findByUserId(clientUuid).orElse(null);
        ProviderProfile providerProfile = providerProfileRepository.findByUserUuid(clientUuid).orElse(null);
        List<ProviderProfile> allProviders = providerProfileRepository.findAll();
        Map<UUID, ProviderProfile> providerByUserUuid = allProviders.stream()
                .filter(p -> p.getUserUuid() != null)
                .collect(Collectors.toMap(ProviderProfile::getUserUuid, p -> p, (a, b) -> a));

        List<Service> allServices = serviceRepository.findAll();
        Map<UUID, Service> serviceById = allServices.stream()
                .filter(s -> s.getId() != null)
                .collect(Collectors.toMap(Service::getId, s -> s, (a, b) -> a));

        List<Appointment> rawAppointments = appointmentRepository != null
                ? appointmentRepository.findByClientUuid(clientUuid)
                : List.of();

        List<Map<String, Object>> enrichedAppointments = new ArrayList<>();
        for (Appointment apt : rawAppointments) {
            ProviderProfile tp = providerByUserUuid.get(apt.getTherapistUuid());
            Service srv = apt.getServiceId() != null ? serviceById.get(apt.getServiceId()) : null;
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", apt.getId());
            item.put("clientUuid", apt.getClientUuid());
            item.put("therapistUuid", apt.getTherapistUuid());
            item.put("therapistName", tp != null ? tp.getDisplayName() : null);
            item.put("organisationName", tp != null && tp.getOrganisation() != null ? tp.getOrganisation().getName() : null);
            item.put("cvrNumber", tp != null && tp.getOrganisation() != null ? tp.getOrganisation().getCvrNumber() : null);
            item.put("serviceId", apt.getServiceId());
            item.put("serviceName", srv != null ? srv.getName() : apt.getTitle());
            item.put("price", srv != null ? srv.getPrice() : null);
            item.put("location", srv != null ? srv.getLocation() : null);
            item.put("startTime", apt.getStartTime());
            item.put("endTime", apt.getEndTime());
            item.put("title", apt.getTitle());
            item.put("status", apt.getStatus());
            enrichedAppointments.add(item);
        }

        List<ClientTherapistHandshake> rawHandshakes = handshakeRepository.findByClientUuid(clientUuid);
        List<Map<String, Object>> enrichedHandshakes = new ArrayList<>();
        for (ClientTherapistHandshake hs : rawHandshakes) {
            ProviderProfile tp = providerByUserUuid.get(hs.getTherapistUuid());
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", hs.getId());
            item.put("clientUuid", hs.getClientUuid());
            item.put("therapistUuid", hs.getTherapistUuid());
            item.put("therapistName", tp != null ? tp.getDisplayName() : hs.getTherapistName());
            item.put("organisationName", tp != null && tp.getOrganisation() != null ? tp.getOrganisation().getName() : hs.getOrganisationName());
            item.put("cvrNumber", tp != null && tp.getOrganisation() != null ? tp.getOrganisation().getCvrNumber() : null);
            item.put("status", hs.getStatus());
            item.put("grantedAt", hs.getGrantedAt());
            item.put("revokedAt", hs.getRevokedAt());
            enrichedHandshakes.add(item);
        }

        List<JournalEntry> journals = journalEntryRepository != null
                ? journalEntryRepository.findByClientUuidOrderByTimestampDesc(clientUuid)
                : List.of();

        List<PsychotraumatologyTest> tests = psychotraumatologyTestRepository != null
                ? psychotraumatologyTestRepository.findByClientUuid(clientUuid)
                : List.of();

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("clientProfile", clientProfile);
        response.put("providerProfile", providerProfile);
        response.put("appointments", enrichedAppointments);
        response.put("handshakes", enrichedHandshakes);
        response.put("journals", journals);
        response.put("pcl5Tests", tests);
        response.put("providers", allProviders);
        response.put("services", allServices);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/clinic/therapist/{therapistUuid}/overview")
    public ResponseEntity<Map<String, Object>> getTherapistOverview(@PathVariable UUID therapistUuid) {
        ProviderProfile provider = providerProfileRepository.findByUserUuid(therapistUuid).orElse(null);

        List<ClientProfile> allClients = clientProfileRepository.findAll();
        Map<UUID, ClientProfile> clientByUserId = allClients.stream()
                .filter(c -> c.getUserId() != null)
                .collect(Collectors.toMap(ClientProfile::getUserId, c -> c, (a, b) -> a));

        List<Service> allServices = serviceRepository.findAll();
        Map<UUID, Service> serviceById = allServices.stream()
                .filter(s -> s.getId() != null)
                .collect(Collectors.toMap(Service::getId, s -> s, (a, b) -> a));

        List<Service> therapistServices = provider == null
                ? allServices
                : allServices.stream()
                        .filter(s -> isTherapistService(s, provider))
                        .collect(Collectors.toList());

        List<Appointment> rawAppointments = appointmentRepository != null
                ? appointmentRepository.findByTherapistUuid(therapistUuid)
                : List.of();

        Map<String, Integer> dailyCounts = new LinkedHashMap<>();
        List<Map<String, Object>> enrichedAppointments = new ArrayList<>();

        for (Appointment apt : rawAppointments) {
            if (!"CANCELLED".equalsIgnoreCase(apt.getStatus()) && apt.getStartTime() != null) {
                String dateKey = apt.getStartTime().toLocalDate().toString();
                dailyCounts.put(dateKey, dailyCounts.getOrDefault(dateKey, 0) + 1);
            }
            ClientProfile cp = clientByUserId.get(apt.getClientUuid());
            Service srv = apt.getServiceId() != null ? serviceById.get(apt.getServiceId()) : null;

            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", apt.getId());
            item.put("clientUuid", apt.getClientUuid());
            item.put("clientName", cp != null ? cp.getFullName() : null);
            item.put("clientPhone", cp != null ? cp.getPhoneNumber() : null);
            item.put("clientEmail", cp != null ? cp.getEmail() : null);
            item.put("therapistUuid", apt.getTherapistUuid());
            item.put("serviceId", apt.getServiceId());
            item.put("serviceName", srv != null ? srv.getName() : apt.getTitle());
            item.put("location", srv != null ? srv.getLocation() : null);
            item.put("price", srv != null ? srv.getPrice() : null);
            item.put("startTime", apt.getStartTime());
            item.put("endTime", apt.getEndTime());
            item.put("title", apt.getTitle());
            item.put("status", apt.getStatus());
            enrichedAppointments.add(item);
        }

        List<ClientTherapistHandshake> handshakes = handshakeRepository.findByTherapistUuid(therapistUuid);
        Set<UUID> rosterClientUuids = new LinkedHashSet<>();
        handshakes.forEach(h -> rosterClientUuids.add(h.getClientUuid()));
        rawAppointments.forEach(a -> rosterClientUuids.add(a.getClientUuid()));
        allClients.forEach(c -> rosterClientUuids.add(c.getUserId()));
        rosterClientUuids.remove(null);
        rosterClientUuids.remove(therapistUuid);

        List<Map<String, Object>> clientRoster = new ArrayList<>();
        for (UUID cUuid : rosterClientUuids) {
            ClientProfile cp = clientByUserId.get(cUuid);
            Optional<ClientTherapistHandshake> hsOpt = handshakes.stream()
                    .filter(h -> cUuid.equals(h.getClientUuid()))
                    .findFirst();

            List<JournalEntry> cJournals = journalEntryRepository != null
                    ? journalEntryRepository.findByClientUuidOrderByTimestampDesc(cUuid)
                    : List.of();
            Integer latestMood = cJournals.isEmpty() ? null : cJournals.get(0).getMoodRating();

            List<PsychotraumatologyTest> cTests = psychotraumatologyTestRepository != null
                    ? psychotraumatologyTestRepository.findByClientUuid(cUuid)
                    : List.of();
            Integer latestPcl5Score = cTests.isEmpty() ? null : cTests.get(cTests.size() - 1).getScore();
            String latestPcl5Severity = cTests.isEmpty() ? null : cTests.get(cTests.size() - 1).getSeverity();

            Optional<Appointment> nextApt = rawAppointments.stream()
                    .filter(a -> cUuid.equals(a.getClientUuid()) && !"CANCELLED".equalsIgnoreCase(a.getStatus()))
                    .sorted(Comparator.comparing(Appointment::getStartTime))
                    .findFirst();

            Map<String, Object> cMap = new LinkedHashMap<>();
            cMap.put("uuid", cUuid);
            cMap.put("name", cp != null ? cp.getFullName() : hsOpt.map(ClientTherapistHandshake::getClientName).orElse(null));
            cMap.put("dob", cp != null && cp.getDateOfBirth() != null ? cp.getDateOfBirth().toString() : null);
            cMap.put("phone", cp != null ? cp.getPhoneNumber() : null);
            cMap.put("email", cp != null ? cp.getEmail() : null);
            cMap.put("address", cp != null ? cp.getHomeAddress() : null);
            cMap.put("postalCode", cp != null ? cp.getPostalCode() : null);
            cMap.put("handshakeId", hsOpt.map(ClientTherapistHandshake::getId).orElse(null));
            cMap.put("handshakeStatus", hsOpt.map(ClientTherapistHandshake::getStatus).orElse("NONE"));
            cMap.put("latestMood", latestMood);
            cMap.put("latestPcl5Score", latestPcl5Score);
            cMap.put("latestPcl5Severity", latestPcl5Severity);
            cMap.put("nextAppointment", nextApt.map(a -> a.getStartTime().toString()).orElse(null));
            clientRoster.add(cMap);
        }

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("therapistUuid", therapistUuid);
        response.put("providerProfile", provider);
        response.put("appointments", enrichedAppointments);
        response.put("dailyCounts", dailyCounts);
        response.put("clients", clientRoster);
        response.put("services", therapistServices);
        return ResponseEntity.ok(response);
    }

    private boolean isTherapistService(Service s, ProviderProfile provider) {
        if (s.getProvider() != null && Objects.equals(provider.getId(), s.getProvider().getId())) {
            return true;
        }
        return provider.getOrganisation() != null
                && s.getOrganisation() != null
                && Objects.equals(provider.getOrganisation().getId(), s.getOrganisation().getId());
    }

    // =========================================================================
    // 1. CLIENT PROFILE REGISTRATION & SETTINGS (Bilag 5.1.1 & Q12)
    // =========================================================================

    @PostMapping("/public/client-profile")
    public ResponseEntity<ClientProfile> savePublicClientProfile(@RequestBody ClientProfile profile) {
        if (profile.getUserId() == null) {
            profile.setUserId(UUID.randomUUID());
        }
        Optional<ClientProfile> existing = clientProfileRepository.findByUserId(profile.getUserId());
        if (existing.isPresent()) {
            ClientProfile current = existing.get();
            current.setFullName(profile.getFullName());
            current.setDateOfBirth(profile.getDateOfBirth());
            current.setHomeAddress(profile.getHomeAddress());
            current.setPostalCode(profile.getPostalCode());
            current.setPhoneNumber(profile.getPhoneNumber());
            current.setEmail(profile.getEmail());
            return ResponseEntity.ok(clientProfileRepository.save(current));
        }
        return ResponseEntity.status(HttpStatus.CREATED).body(clientProfileRepository.save(profile));
    }

    @PutMapping("/clinic/client-profile")
    public ResponseEntity<ClientProfile> updateClientProfile(@RequestBody ClientProfile profile) {
        return savePublicClientProfile(profile);
    }

    @GetMapping("/clinic/client-profile/{userUuid}")
    public ResponseEntity<ClientProfile> getClientProfile(@PathVariable UUID userUuid) {
        return clientProfileRepository.findByUserId(userUuid)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    // =========================================================================
    // 2. THERAPIST ONBOARDING & CVR ORGANISATIONS (Bilag 5.1.2 & Q2)
    // =========================================================================

    @GetMapping("/clinic/organisations")
    public ResponseEntity<List<Organisation>> getOrganisations() {
        return ResponseEntity.ok(organisationRepository.findAll());
    }

    public ResponseEntity<ProviderProfile> onboardTherapist(Map<String, String> payload) {
        return onboardTherapist(payload, null);
    }

    @PostMapping("/clinic/therapist-onboarding")
    public ResponseEntity<ProviderProfile> onboardTherapist(
            @RequestBody Map<String, String> payload,
            Authentication auth
    ) {
        String cvr = payload.getOrDefault("cvrNumber", "").trim();
        String orgName = payload.getOrDefault("organisationName", "").trim();
        if (cvr.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "CVR number is required");
        }

        Organisation organisation = organisationRepository.findByCvrNumber(cvr)
                .orElseGet(() -> {
                    if (orgName.isEmpty()) {
                        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Organisation name is required when creating a new CVR");
                    }
                    return organisationRepository.save(new Organisation(cvr, orgName));
                });

        UUID userUuid = payload.get("userUuid") != null
                ? UUID.fromString(payload.get("userUuid").trim())
                : (auth != null && auth.getPrincipal() instanceof Jwt jwt ? extractUserUuid(jwt) : UUID.randomUUID());

        ProviderProfile profile = providerProfileRepository.findByUserUuid(userUuid)
                .orElseGet(ProviderProfile::new);
        profile.setUserUuid(userUuid);
        profile.setDisplayName(payload.getOrDefault("displayName", ""));
        profile.setOrganisation(organisation);
        profile.setBiography(payload.getOrDefault("biography", payload.getOrDefault("bioDa", "")));
        profile.setWorkingHoursJson(payload.getOrDefault("workingHoursJson", ""));

        return ResponseEntity.ok(providerProfileRepository.save(profile));
    }

    // =========================================================================
    // 3. HYBRID SERVICE CATALOG & MARKETPLACE DIRECTORY (Bilag 5.1.3, Q7, Q8)
    // =========================================================================

    @GetMapping("/clinic/services")
    public ResponseEntity<List<Service>> getAllServices() {
        return ResponseEntity.ok(serviceRepository.findAll());
    }

    @PostMapping("/clinic/services")
    public ResponseEntity<Service> createService(@RequestBody Map<String, Object> payload) {
        String name = String.valueOf(payload.getOrDefault("name", payload.getOrDefault("nameDa", ""))).trim();
        String description = String.valueOf(payload.getOrDefault("description", payload.getOrDefault("descriptionDa", ""))).trim();
        String priceStr = String.valueOf(payload.getOrDefault("price", payload.getOrDefault("priceDkk", "0.0")));
        int durationMinutes = Integer.parseInt(String.valueOf(payload.getOrDefault("durationMinutes", "60")));
        double price = Double.parseDouble(priceStr);
        String location = String.valueOf(payload.getOrDefault("location", "")).trim();

        ProviderProfile provider = null;
        if (payload.get("providerProfileId") != null && !String.valueOf(payload.get("providerProfileId")).isBlank()) {
            UUID providerId = UUID.fromString(payload.get("providerProfileId").toString());
            provider = providerProfileRepository.findById(providerId).orElse(null);
        }

        Organisation organisation = null;
        if (payload.get("organisationId") != null && !String.valueOf(payload.get("organisationId")).isBlank()) {
            UUID orgId = UUID.fromString(payload.get("organisationId").toString());
            organisation = organisationRepository.findById(orgId).orElse(null);
        }

        final UUID targetProviderId = provider != null ? provider.getId() : null;
        final UUID targetOrgId = organisation != null ? organisation.getId() : null;

        // Deduplicate: if a service with the same name for the same provider/organisation already exists, update it instead of creating a duplicate row
        List<Service> matching = serviceRepository.findAll().stream()
                .filter(s -> s.getName() != null && s.getName().trim().equalsIgnoreCase(name))
                .filter(s -> Objects.equals(s.getProvider() != null ? s.getProvider().getId() : null, targetProviderId))
                .filter(s -> Objects.equals(s.getOrganisation() != null ? s.getOrganisation().getId() : null, targetOrgId))
                .collect(Collectors.toList());

        Service service;
        if (!matching.isEmpty()) {
            service = matching.get(0);
            if (matching.size() > 1) {
                for (int i = 1; i < matching.size(); i++) {
                    serviceRepository.delete(matching.get(i));
                }
            }
        } else {
            service = new Service();
        }

        service.setName(name);
        service.setDescription(description);
        service.setDurationMinutes(durationMinutes);
        service.setPrice(price);
        service.setLocation(location);
        service.setIsActive(true);
        service.setProvider(provider);
        service.setOrganisation(organisation);

        return ResponseEntity.status(HttpStatus.CREATED).body(serviceRepository.save(service));
    }

    @PutMapping("/clinic/services/{id}")
    public ResponseEntity<Service> updateService(
            @PathVariable UUID id,
            @RequestBody Map<String, Object> payload
    ) {
        Service service = serviceRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Service not found"));

        if (payload.containsKey("name") || payload.containsKey("nameDa")) {
            service.setName(String.valueOf(payload.getOrDefault("name", payload.get("nameDa"))).trim());
        }
        if (payload.containsKey("description") || payload.containsKey("descriptionDa")) {
            service.setDescription(String.valueOf(payload.getOrDefault("description", payload.get("descriptionDa"))).trim());
        }
        if (payload.containsKey("durationMinutes")) {
            service.setDurationMinutes(Integer.parseInt(String.valueOf(payload.get("durationMinutes"))));
        }
        if (payload.containsKey("price") || payload.containsKey("priceDkk")) {
            service.setPrice(Double.parseDouble(String.valueOf(payload.getOrDefault("price", payload.get("priceDkk")))));
        }
        if (payload.containsKey("location")) {
            service.setLocation(String.valueOf(payload.get("location")).trim());
        }
        if (payload.containsKey("isActive") && payload.get("isActive") != null) {
            service.setIsActive(Boolean.parseBoolean(String.valueOf(payload.get("isActive"))));
        }
        if (payload.containsKey("providerProfileId")) {
            Object pIdObj = payload.get("providerProfileId");
            if (pIdObj != null && !String.valueOf(pIdObj).isBlank()) {
                UUID providerId = UUID.fromString(String.valueOf(pIdObj));
                providerProfileRepository.findById(providerId).ifPresent(service::setProvider);
            } else {
                service.setProvider(null);
            }
        }
        if (payload.containsKey("organisationId")) {
            Object oIdObj = payload.get("organisationId");
            if (oIdObj != null && !String.valueOf(oIdObj).isBlank()) {
                UUID orgId = UUID.fromString(String.valueOf(oIdObj));
                organisationRepository.findById(orgId).ifPresent(service::setOrganisation);
            } else {
                service.setOrganisation(null);
            }
        }

        return ResponseEntity.ok(serviceRepository.save(service));
    }

    @DeleteMapping("/clinic/services/{id}")
    public ResponseEntity<Map<String, Object>> deleteService(@PathVariable UUID id) {
        Service service = serviceRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Service not found"));

        // Delete any duplicate rows sharing the same name + provider + organisation so deleting cleans up all duplicates at once
        String targetName = service.getName() != null ? service.getName().trim() : "";
        UUID targetProviderId = service.getProvider() != null ? service.getProvider().getId() : null;
        UUID targetOrgId = service.getOrganisation() != null ? service.getOrganisation().getId() : null;

        List<Service> toDelete = serviceRepository.findAll().stream()
                .filter(s -> Objects.equals(s.getId(), id) || (
                        s.getName() != null
                                && s.getName().trim().equalsIgnoreCase(targetName)
                                && Objects.equals(s.getProvider() != null ? s.getProvider().getId() : null, targetProviderId)
                                && Objects.equals(s.getOrganisation() != null ? s.getOrganisation().getId() : null, targetOrgId)
                ))
                .collect(Collectors.toList());

        serviceRepository.deleteAll(toDelete);
        Map<String, Object> res = new LinkedHashMap<>();
        res.put("deletedId", id);
        res.put("deletedCount", toDelete.size());
        return ResponseEntity.ok(res);
    }

    @PatchMapping("/clinic/services/{id}/toggle")
    public ResponseEntity<Service> toggleServiceActive(@PathVariable UUID id) {
        Service service = serviceRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Service not found"));
        service.setIsActive(!service.getIsActive());
        return ResponseEntity.ok(serviceRepository.save(service));
    }

    // =========================================================================
    // 4. CLIENT-THERAPIST HANDSHAKES & CRM ROSTER (Q3, Q4, Q10)
    // =========================================================================

    @GetMapping("/clinic/handshakes/client/{clientUuid}")
    public ResponseEntity<List<ClientTherapistHandshake>> getClientHandshakes(@PathVariable UUID clientUuid) {
        return ResponseEntity.ok(handshakeRepository.findByClientUuid(clientUuid));
    }

    @GetMapping("/clinic/handshakes/therapist/{therapistUuid}")
    public ResponseEntity<List<ClientTherapistHandshake>> getTherapistHandshakes(@PathVariable UUID therapistUuid) {
        return ResponseEntity.ok(handshakeRepository.findByTherapistUuid(therapistUuid));
    }

    @PostMapping("/clinic/handshakes")
    public ResponseEntity<ClientTherapistHandshake> createOrUpdateHandshake(@RequestBody ClientTherapistHandshake req) {
        if (req.getClientName() == null && req.getClientUuid() != null) {
            clientProfileRepository.findByUserId(req.getClientUuid())
                    .ifPresent(cp -> req.setClientName(cp.getFullName()));
        }
        if (req.getTherapistUuid() != null) {
            providerProfileRepository.findByUserUuid(req.getTherapistUuid()).ifPresent(tp -> {
                if (req.getTherapistName() == null) {
                    req.setTherapistName(tp.getDisplayName());
                }
                if (req.getOrganisationName() == null && tp.getOrganisation() != null) {
                    req.setOrganisationName(tp.getOrganisation().getName());
                }
            });
        }

        Optional<ClientTherapistHandshake> existing = handshakeRepository.findByClientUuidAndTherapistUuid(
                req.getClientUuid(), req.getTherapistUuid()
        );
        if (existing.isPresent()) {
            ClientTherapistHandshake current = existing.get();
            current.setClientName(Objects.requireNonNullElse(req.getClientName(), current.getClientName()));
            current.setTherapistName(Objects.requireNonNullElse(req.getTherapistName(), current.getTherapistName()));
            current.setOrganisationName(Objects.requireNonNullElse(req.getOrganisationName(), current.getOrganisationName()));
            current.setStatus(Objects.requireNonNullElse(req.getStatus(), "ACTIVE"));
            if ("ACTIVE".equalsIgnoreCase(current.getStatus())) {
                current.setGrantedAt(LocalDateTime.now());
                current.setRevokedAt(null);
            }
            return ResponseEntity.ok(handshakeRepository.save(current));
        }
        req.setStatus(Objects.requireNonNullElse(req.getStatus(), "ACTIVE"));
        if ("ACTIVE".equalsIgnoreCase(req.getStatus())) {
            req.setGrantedAt(LocalDateTime.now());
        }
        return ResponseEntity.status(HttpStatus.CREATED).body(handshakeRepository.save(req));
    }

    @PatchMapping("/clinic/handshakes/{id}/status")
    public ResponseEntity<ClientTherapistHandshake> updateHandshakeStatus(
            @PathVariable UUID id,
            @RequestBody Map<String, String> body
    ) {
        ClientTherapistHandshake handshake = handshakeRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Handshake not found"));
        String status = body.getOrDefault("status", "REVOKED").toUpperCase();
        handshake.setStatus(status);
        if ("REVOKED".equals(status)) {
            handshake.setRevokedAt(LocalDateTime.now());
        } else if ("ACTIVE".equals(status)) {
            handshake.setGrantedAt(LocalDateTime.now());
            handshake.setRevokedAt(null);
        }
        return ResponseEntity.ok(handshakeRepository.save(handshake));
    }

    private Jwt extractJwt(Authentication auth) {
        if (auth != null && auth.getPrincipal() instanceof Jwt jwt) {
            return jwt;
        }
        throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Valid authenticated user is required");
    }

    private UUID extractUserUuid(Jwt jwt) {
        if (jwt.getSubject() != null) {
            try {
                return UUID.fromString(jwt.getSubject());
            } catch (IllegalArgumentException ignored) {
            }
        }
        throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Valid authenticated user UUID is required");
    }

    private String extractClaim(Jwt jwt, String claim, String fallback) {
        String val = jwt.getClaimAsString(claim);
        return (val != null && !val.isBlank()) ? val : fallback;
    }

    private List<String> extractRoles(Jwt jwt) {
        Map<String, Object> realmAccess = jwt.getClaimAsMap("realm_access");
        if (realmAccess != null && realmAccess.get("roles") instanceof List<?> list) {
            return list.stream().map(String::valueOf).collect(Collectors.toList());
        }
        return List.of();
    }
}
