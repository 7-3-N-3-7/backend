package com.platform.service;

import com.platform.core.service.BaseService;
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
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class ClinicPlatformService extends BaseService<ClientProfile, ClientProfile, UUID> {

    private final ClientProfileRepository clientProfileRepository;
    private final OrganisationRepository organisationRepository;
    private final ProviderProfileRepository providerProfileRepository;
    private final ServiceRepository serviceRepository;
    private final HandshakeRepository handshakeRepository;
    private final AppointmentRepository appointmentRepository;
    private final JournalEntryRepository journalEntryRepository;
    private final PsychotraumatologyTestRepository psychotraumatologyTestRepository;

    @Autowired
    public ClinicPlatformService(
            ClientProfileRepository clientProfileRepository,
            OrganisationRepository organisationRepository,
            ProviderProfileRepository providerProfileRepository,
            ServiceRepository serviceRepository,
            HandshakeRepository handshakeRepository,
            @Autowired(required = false) AppointmentRepository appointmentRepository,
            @Autowired(required = false) JournalEntryRepository journalEntryRepository,
            @Autowired(required = false) PsychotraumatologyTestRepository psychotraumatologyTestRepository
    ) {
        super(clientProfileRepository);
        this.clientProfileRepository = clientProfileRepository;
        this.organisationRepository = organisationRepository;
        this.providerProfileRepository = providerProfileRepository;
        this.serviceRepository = serviceRepository;
        this.handshakeRepository = handshakeRepository;
        this.appointmentRepository = appointmentRepository;
        this.journalEntryRepository = journalEntryRepository;
        this.psychotraumatologyTestRepository = psychotraumatologyTestRepository;
    }

    public Map<String, Object> getCurrentUserContext(Authentication auth) {
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
        if (isTherapist) {
            List<String> missing = getMissingProfileFields(providerProfile, null, clientProfile);
            result.put("canCreateServices", missing.isEmpty());
            result.put("missingProfileFields", missing);
        }
        return result;
    }

    public Map<String, Object> getMarketplaceDirectory() {
        List<Organisation> organisations = organisationRepository.findAll();
        List<ProviderProfile> providers = providerProfileRepository.findAll();
        List<com.platform.entity.Service> services = serviceRepository.findAll();

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("organisations", organisations);
        response.put("providers", providers);
        response.put("services", services);
        return response;
    }

    public Map<String, Object> getClientOverview(UUID clientUuid) {
        ClientProfile clientProfile = clientProfileRepository.findByUserId(clientUuid).orElse(null);
        ProviderProfile providerProfile = providerProfileRepository.findByUserUuid(clientUuid).orElse(null);
        List<ProviderProfile> allProviders = providerProfileRepository.findAll();
        Map<UUID, ProviderProfile> providerByUserUuid = allProviders.stream()
                .filter(p -> p.getUserUuid() != null)
                .collect(Collectors.toMap(ProviderProfile::getUserUuid, p -> p, (a, b) -> a));

        List<com.platform.entity.Service> allServices = serviceRepository.findAll();
        Map<UUID, com.platform.entity.Service> serviceById = allServices.stream()
                .filter(s -> s.getId() != null)
                .collect(Collectors.toMap(com.platform.entity.Service::getId, s -> s, (a, b) -> a));

        List<Appointment> rawAppointments = appointmentRepository != null
                ? appointmentRepository.findByClientUuid(clientUuid)
                : List.of();

        List<Map<String, Object>> enrichedAppointments = new ArrayList<>();
        for (Appointment apt : rawAppointments) {
            ProviderProfile tp = providerByUserUuid.get(apt.getTherapistUuid());
            com.platform.entity.Service srv = apt.getServiceId() != null ? serviceById.get(apt.getServiceId()) : null;
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", apt.getId());
            item.put("clientUuid", apt.getClientUuid());
            item.put("therapistUuid", apt.getTherapistUuid());
            item.put("therapistName", tp != null ? tp.getDisplayName() : null);
            item.put("organisationName", tp != null && tp.getOrganisation() != null ? tp.getOrganisation().getName() : null);
            item.put("cvrNumber", tp != null && tp.getOrganisation() != null ? tp.getOrganisation().getCvrNumber() : null);
            item.put("serviceId", apt.getServiceId());
            item.put("serviceName", srv != null ? srv.getName() : apt.getTitle());
            String effectiveLocation = apt.getLocation();
            if (effectiveLocation == null || effectiveLocation.isBlank()) {
                effectiveLocation = srv != null ? srv.getLocation() : null;
            }
            String videoMeetingUrl = tp != null ? tp.getVideoMeetingUrl() : null;
            if ((videoMeetingUrl == null || videoMeetingUrl.isBlank()) && tp != null && tp.getOrganisation() != null) {
                videoMeetingUrl = tp.getOrganisation().getVideoMeetingUrl();
            }
            if (effectiveLocation != null && (effectiveLocation.toLowerCase().contains("online") || effectiveLocation.toLowerCase().contains("video"))) {
                if (videoMeetingUrl != null && !videoMeetingUrl.isBlank()) {
                    effectiveLocation = "Online: " + videoMeetingUrl;
                }
            }
            item.put("price", srv != null ? srv.getPrice() : null);
            item.put("location", effectiveLocation);
            item.put("videoMeetingUrl", videoMeetingUrl);
            item.put("startTime", apt.getStartTime());
            item.put("endTime", apt.getEndTime());
            item.put("title", apt.getTitle());
            item.put("status", apt.getStatus());
            item.put("gitCommitHash", apt.getGitCommitHash());
            item.put("journalEntryId", apt.getJournalEntryId());
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
            item.put("validFrom", hs.getValidFrom());
            item.put("validTo", hs.getValidTo());
            item.put("historyStartDate", hs.getHistoryStartDate());
            item.put("historyEndDate", hs.getHistoryEndDate());
            item.put("isCurrentlyValid", hs.isCurrentlyValid());
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
        return response;
    }

    public Map<String, Object> getTherapistOverview(UUID therapistUuid) {
        ProviderProfile provider = providerProfileRepository.findByUserUuid(therapistUuid).orElse(null);

        List<ClientProfile> allClients = clientProfileRepository.findAll();
        Map<UUID, ClientProfile> clientByUserId = allClients.stream()
                .filter(c -> c.getUserId() != null)
                .collect(Collectors.toMap(ClientProfile::getUserId, c -> c, (a, b) -> a));

        List<com.platform.entity.Service> allServices = serviceRepository.findAll();
        Map<UUID, com.platform.entity.Service> serviceById = allServices.stream()
                .filter(s -> s.getId() != null)
                .collect(Collectors.toMap(com.platform.entity.Service::getId, s -> s, (a, b) -> a));

        List<com.platform.entity.Service> therapistServices = provider == null
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
            com.platform.entity.Service srv = apt.getServiceId() != null ? serviceById.get(apt.getServiceId()) : null;
            String effectiveLocation = apt.getLocation();
            if (effectiveLocation == null || effectiveLocation.isBlank()) {
                effectiveLocation = srv != null ? srv.getLocation() : null;
            }
            if (effectiveLocation != null && (effectiveLocation.toLowerCase().contains("klient") || effectiveLocation.toLowerCase().contains("client") || effectiveLocation.toLowerCase().contains("hjemmebesøg"))) {
                if (cp != null && cp.getHomeAddress() != null && !cp.getHomeAddress().isBlank()) {
                    effectiveLocation = "Hos klienten: " + cp.getHomeAddress().trim() + (cp.getPostalCode() != null && !cp.getPostalCode().isBlank() ? ", " + cp.getPostalCode().trim() : "");
                }
            }

            String videoMeetingUrl = provider != null ? provider.getVideoMeetingUrl() : null;
            if ((videoMeetingUrl == null || videoMeetingUrl.isBlank()) && provider != null && provider.getOrganisation() != null) {
                videoMeetingUrl = provider.getOrganisation().getVideoMeetingUrl();
            }
            if (effectiveLocation != null && (effectiveLocation.toLowerCase().contains("online") || effectiveLocation.toLowerCase().contains("video"))) {
                if (videoMeetingUrl != null && !videoMeetingUrl.isBlank()) {
                    effectiveLocation = "Online: " + videoMeetingUrl;
                }
            }

            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", apt.getId());
            item.put("clientUuid", apt.getClientUuid());
            item.put("clientName", cp != null ? cp.getFullName() : null);
            item.put("clientPhone", cp != null ? cp.getPhoneNumber() : null);
            item.put("clientEmail", cp != null ? cp.getEmail() : null);
            item.put("clientAddress", cp != null ? cp.getHomeAddress() : null);
            item.put("clientPostalCode", cp != null ? cp.getPostalCode() : null);
            item.put("therapistUuid", apt.getTherapistUuid());
            item.put("serviceId", apt.getServiceId());
            item.put("serviceName", srv != null ? srv.getName() : apt.getTitle());
            item.put("location", effectiveLocation);
            item.put("videoMeetingUrl", videoMeetingUrl);
            item.put("price", srv != null ? srv.getPrice() : null);
            item.put("startTime", apt.getStartTime());
            item.put("endTime", apt.getEndTime());
            item.put("title", apt.getTitle());
            item.put("status", apt.getStatus());
            item.put("gitCommitHash", apt.getGitCommitHash());
            item.put("journalEntryId", apt.getJournalEntryId());
            enrichedAppointments.add(item);
        }

        List<ClientTherapistHandshake> handshakes = handshakeRepository.findByTherapistUuid(therapistUuid);
        Set<UUID> rosterClientUuids = new LinkedHashSet<>();
        handshakes.forEach(h -> rosterClientUuids.add(h.getClientUuid()));
        rawAppointments.forEach(a -> rosterClientUuids.add(a.getClientUuid()));
        rosterClientUuids.remove(null);
        rosterClientUuids.remove(therapistUuid);

        List<Map<String, Object>> clientRoster = new ArrayList<>();
        for (UUID cUuid : rosterClientUuids) {
            ClientProfile cp = clientByUserId.get(cUuid);
            Optional<ClientTherapistHandshake> hsOpt = handshakes.stream()
                    .filter(h -> cUuid.equals(h.getClientUuid()))
                    .findFirst();

            boolean hasActiveHandshake = hsOpt.map(h -> "ACTIVE".equalsIgnoreCase(h.getStatus())).orElse(false);
            Integer latestMood = null;
            Integer latestPcl5Score = null;
            String latestPcl5Severity = null;

            if (hasActiveHandshake) {
                List<JournalEntry> cJournals = journalEntryRepository != null
                        ? journalEntryRepository.findByClientUuidOrderByTimestampDesc(cUuid)
                        : List.of();
                latestMood = cJournals.isEmpty() ? null : cJournals.get(0).getMoodRating();

                List<PsychotraumatologyTest> cTests = psychotraumatologyTestRepository != null
                        ? psychotraumatologyTestRepository.findByClientUuid(cUuid)
                        : List.of();
                latestPcl5Score = cTests.isEmpty() ? null : cTests.get(cTests.size() - 1).getScore();
                latestPcl5Severity = cTests.isEmpty() ? null : cTests.get(cTests.size() - 1).getSeverity();
            }

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

        ClientProfile therapistClientProfile = clientProfileRepository.findByUserId(therapistUuid).orElse(null);
        List<String> missingFields = getMissingProfileFields(provider, null, therapistClientProfile);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("therapistUuid", therapistUuid);
        response.put("providerProfile", provider);
        response.put("clientProfile", therapistClientProfile);
        response.put("canCreateServices", missingFields.isEmpty());
        response.put("missingProfileFields", missingFields);
        response.put("appointments", enrichedAppointments);
        response.put("dailyCounts", dailyCounts);
        response.put("clients", clientRoster);
        response.put("services", therapistServices);
        return response;
    }

    private boolean isTherapistService(com.platform.entity.Service s, ProviderProfile provider) {
        if (s.getProvider() != null && Objects.equals(provider.getId(), s.getProvider().getId())) {
            return true;
        }
        return provider.getOrganisation() != null
                && s.getOrganisation() != null
                && Objects.equals(provider.getOrganisation().getId(), s.getOrganisation().getId());
    }

    public ResponseEntity<ClientProfile> savePublicClientProfile(ClientProfile profile) {
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

    public Optional<ClientProfile> getClientProfileByUserId(UUID userUuid) {
        return clientProfileRepository.findByUserId(userUuid);
    }

    public List<Organisation> getOrganisations() {
        return organisationRepository.findAll();
    }

    public ProviderProfile onboardTherapist(Map<String, String> payload, Authentication auth) {
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

        if (!orgName.isEmpty()) {
            organisation.setName(orgName);
        }
        if (payload.containsKey("address") && payload.get("address") != null) {
            organisation.setAddress(payload.get("address").trim());
        }
        if (payload.containsKey("postalCode") && payload.get("postalCode") != null) {
            organisation.setPostalCode(payload.get("postalCode").trim());
        }
        if (payload.containsKey("phoneNumber") && payload.get("phoneNumber") != null) {
            organisation.setPhoneNumber(payload.get("phoneNumber").trim());
        }
        if (payload.containsKey("email") && payload.get("email") != null) {
            organisation.setEmail(payload.get("email").trim());
        }
        if (payload.containsKey("videoMeetingUrl") && payload.get("videoMeetingUrl") != null) {
            organisation.setVideoMeetingUrl(payload.get("videoMeetingUrl").trim());
        }
        organisationRepository.save(organisation);

        UUID userUuid = null;
        String rawUserUuid = payload.get("userUuid");
        if (rawUserUuid != null && !rawUserUuid.trim().isEmpty() && !"undefined".equalsIgnoreCase(rawUserUuid.trim()) && !"null".equalsIgnoreCase(rawUserUuid.trim())) {
            try {
                userUuid = UUID.fromString(rawUserUuid.trim());
            } catch (IllegalArgumentException ignored) {
            }
        }
        if (userUuid == null) {
            userUuid = (auth != null && auth.getPrincipal() instanceof Jwt jwt)
                    ? extractUserUuid(jwt)
                    : UUID.randomUUID();
        }

        ProviderProfile profile = providerProfileRepository.findByUserUuid(userUuid)
                .orElseGet(ProviderProfile::new);
        profile.setUserUuid(userUuid);
        profile.setDisplayName(payload.getOrDefault("displayName", ""));
        profile.setOrganisation(organisation);
        profile.setBiography(payload.getOrDefault("biography", payload.getOrDefault("bioDa", "")));
        profile.setWorkingHoursJson(payload.getOrDefault("workingHoursJson", ""));
        if (payload.containsKey("videoMeetingUrl") && payload.get("videoMeetingUrl") != null) {
            profile.setVideoMeetingUrl(payload.get("videoMeetingUrl").trim());
        } else if (profile.getVideoMeetingUrl() == null || profile.getVideoMeetingUrl().isBlank()) {
            profile.setVideoMeetingUrl(organisation.getVideoMeetingUrl());
        }

        return providerProfileRepository.save(profile);
    }

    public List<com.platform.entity.Service> getAllServices() {
        return serviceRepository.findAll();
    }

    public List<String> getMissingProfileFields(ProviderProfile provider, Organisation organisation, ClientProfile clientProfile) {
        Organisation org = organisation != null ? organisation : (provider != null ? provider.getOrganisation() : null);
        List<String> missing = new ArrayList<>();
        if (org == null || org.getCvrNumber() == null || org.getCvrNumber().trim().isBlank()) {
            missing.add("CVR-nummer");
        }
        if (org == null || org.getName() == null || org.getName().trim().isBlank()) {
            missing.add("organisationsnavn");
        }
        if (provider == null || provider.getWorkingHoursJson() == null || provider.getWorkingHoursJson().trim().isBlank()) {
            missing.add("arbejdstider");
        }

        String address = (org != null && org.getAddress() != null && !org.getAddress().trim().isBlank())
                ? org.getAddress().trim()
                : (clientProfile != null && clientProfile.getHomeAddress() != null ? clientProfile.getHomeAddress().trim() : "");
        if (address.isBlank()) {
            missing.add("hjemmeadresse");
        }

        String postalCode = (org != null && org.getPostalCode() != null && !org.getPostalCode().trim().isBlank())
                ? org.getPostalCode().trim()
                : (clientProfile != null && clientProfile.getPostalCode() != null ? clientProfile.getPostalCode().trim() : "");
        if (postalCode.isBlank()) {
            missing.add("postnummer");
        }

        String phoneNumber = (org != null && org.getPhoneNumber() != null && !org.getPhoneNumber().trim().isBlank())
                ? org.getPhoneNumber().trim()
                : (clientProfile != null && clientProfile.getPhoneNumber() != null ? clientProfile.getPhoneNumber().trim() : "");
        if (phoneNumber.isBlank()) {
            missing.add("telefonnummer");
        }
        return missing;
    }

    public com.platform.entity.Service createService(Map<String, Object> payload, Authentication auth) {
        String name = String.valueOf(payload.getOrDefault("name", payload.getOrDefault("nameDa", ""))).trim();
        String description = String.valueOf(payload.getOrDefault("description", payload.getOrDefault("descriptionDa", ""))).trim();
        String priceStr = String.valueOf(payload.getOrDefault("price", payload.getOrDefault("priceDkk", "0.0")));
        int durationMinutes = Integer.parseInt(String.valueOf(payload.getOrDefault("durationMinutes", "60")));
        double price = Double.parseDouble(priceStr);
        String location = String.valueOf(payload.getOrDefault("location", "")).trim();

        UUID therapistUuid = null;
        if (payload.get("therapistUuid") != null && !String.valueOf(payload.get("therapistUuid")).isBlank()) {
            therapistUuid = UUID.fromString(payload.get("therapistUuid").toString().trim());
        } else if (payload.get("userUuid") != null && !String.valueOf(payload.get("userUuid")).isBlank()) {
            therapistUuid = UUID.fromString(payload.get("userUuid").toString().trim());
        }

        ProviderProfile provider = null;
        if (payload.get("providerProfileId") != null && !String.valueOf(payload.get("providerProfileId")).isBlank()) {
            UUID providerId = UUID.fromString(payload.get("providerProfileId").toString().trim());
            provider = providerProfileRepository.findById(providerId).orElse(null);
            if (provider != null && therapistUuid == null) {
                therapistUuid = provider.getUserUuid();
            }
        }

        if (therapistUuid == null && auth != null && auth.getPrincipal() instanceof Jwt jwt) {
            therapistUuid = extractUserUuid(jwt);
        }

        if (provider == null && therapistUuid != null) {
            provider = providerProfileRepository.findByUserUuid(therapistUuid).orElse(null);
        }

        Organisation organisation = null;
        if (payload.get("organisationId") != null && !String.valueOf(payload.get("organisationId")).isBlank()) {
            UUID orgId = UUID.fromString(payload.get("organisationId").toString().trim());
            organisation = organisationRepository.findById(orgId).orElse(null);
        }
        if (organisation == null && provider != null && provider.getOrganisation() != null) {
            organisation = provider.getOrganisation();
        }

        ClientProfile clientProfile = therapistUuid != null
                ? clientProfileRepository.findByUserId(therapistUuid).orElse(null)
                : (provider != null && provider.getUserUuid() != null
                        ? clientProfileRepository.findByUserId(provider.getUserUuid()).orElse(null)
                        : null);

        List<String> missing = getMissingProfileFields(provider, organisation, clientProfile);
        if (!missing.isEmpty()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Du kan ikke oprette en service/ydelse før du har udfyldt: " + String.join(", ", missing)
            );
        }

        final UUID targetProviderId = provider != null ? provider.getId() : null;
        final UUID targetOrgId = organisation != null ? organisation.getId() : null;

        List<com.platform.entity.Service> matching = serviceRepository.findAll().stream()
                .filter(s -> s.getName() != null && s.getName().trim().equalsIgnoreCase(name))
                .filter(s -> Objects.equals(s.getProvider() != null ? s.getProvider().getId() : null, targetProviderId))
                .filter(s -> Objects.equals(s.getOrganisation() != null ? s.getOrganisation().getId() : null, targetOrgId))
                .collect(Collectors.toList());

        com.platform.entity.Service service;
        if (!matching.isEmpty()) {
            service = matching.get(0);
            if (matching.size() > 1) {
                for (int i = 1; i < matching.size(); i++) {
                    serviceRepository.delete(matching.get(i));
                }
            }
        } else {
            service = new com.platform.entity.Service();
        }

        service.setName(name);
        service.setDescription(description);
        service.setDurationMinutes(durationMinutes);
        service.setPrice(price);
        service.setLocation(location);
        service.setIsActive(true);
        service.setProvider(provider);
        service.setOrganisation(organisation);

        return serviceRepository.save(service);
    }

    public com.platform.entity.Service updateService(UUID id, Map<String, Object> payload, Authentication auth) {
        com.platform.entity.Service service = serviceRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Service not found"));

        UUID therapistUuid = null;
        if (payload.get("therapistUuid") != null && !String.valueOf(payload.get("therapistUuid")).isBlank()) {
            therapistUuid = UUID.fromString(payload.get("therapistUuid").toString().trim());
        } else if (payload.get("userUuid") != null && !String.valueOf(payload.get("userUuid")).isBlank()) {
            therapistUuid = UUID.fromString(payload.get("userUuid").toString().trim());
        }

        ProviderProfile provider = service.getProvider();
        if (payload.containsKey("providerProfileId")) {
            Object pIdObj = payload.get("providerProfileId");
            if (pIdObj != null && !String.valueOf(pIdObj).isBlank()) {
                UUID providerId = UUID.fromString(String.valueOf(pIdObj).trim());
                provider = providerProfileRepository.findById(providerId).orElse(null);
            }
        }
        if (provider != null && therapistUuid == null) {
            therapistUuid = provider.getUserUuid();
        }
        if (therapistUuid == null && auth != null && auth.getPrincipal() instanceof Jwt jwt) {
            therapistUuid = extractUserUuid(jwt);
        }
        if (provider == null && therapistUuid != null) {
            provider = providerProfileRepository.findByUserUuid(therapistUuid).orElse(null);
        }

        Organisation organisation = service.getOrganisation();
        if (payload.containsKey("organisationId")) {
            Object oIdObj = payload.get("organisationId");
            if (oIdObj != null && !String.valueOf(oIdObj).isBlank()) {
                UUID orgId = UUID.fromString(String.valueOf(oIdObj).trim());
                organisation = organisationRepository.findById(orgId).orElse(null);
            }
        }
        if (organisation == null && provider != null && provider.getOrganisation() != null) {
            organisation = provider.getOrganisation();
        }

        ClientProfile clientProfile = therapistUuid != null
                ? clientProfileRepository.findByUserId(therapistUuid).orElse(null)
                : (provider != null && provider.getUserUuid() != null
                        ? clientProfileRepository.findByUserId(provider.getUserUuid()).orElse(null)
                        : null);

        List<String> missing = getMissingProfileFields(provider, organisation, clientProfile);
        if (!missing.isEmpty()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Du kan ikke opdatere en service/ydelse før du har udfyldt: " + String.join(", ", missing)
            );
        }

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
            service.setProvider(provider);
        }
        if (payload.containsKey("organisationId")) {
            service.setOrganisation(organisation);
        }

        return serviceRepository.save(service);
    }

    public Map<String, Object> deleteService(UUID id) {
        com.platform.entity.Service service = serviceRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Service not found"));

        String targetName = service.getName() != null ? service.getName().trim() : "";
        UUID targetProviderId = service.getProvider() != null ? service.getProvider().getId() : null;
        UUID targetOrgId = service.getOrganisation() != null ? service.getOrganisation().getId() : null;

        List<com.platform.entity.Service> toDelete = serviceRepository.findAll().stream()
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
        return res;
    }

    public com.platform.entity.Service toggleServiceActive(UUID id) {
        com.platform.entity.Service service = serviceRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Service not found"));
        service.setIsActive(!service.getIsActive());
        return serviceRepository.save(service);
    }

    public List<ClientTherapistHandshake> getClientHandshakes(UUID clientUuid) {
        return handshakeRepository.findByClientUuid(clientUuid);
    }

    public List<ClientTherapistHandshake> getTherapistHandshakes(UUID therapistUuid) {
        return handshakeRepository.findByTherapistUuid(therapistUuid);
    }

    public ResponseEntity<ClientTherapistHandshake> createOrUpdateHandshake(ClientTherapistHandshake req) {
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
            if (req.getValidFrom() != null) current.setValidFrom(req.getValidFrom());
            if (req.getValidTo() != null) current.setValidTo(req.getValidTo());
            if (req.getHistoryStartDate() != null) current.setHistoryStartDate(req.getHistoryStartDate());
            if (req.getHistoryEndDate() != null) current.setHistoryEndDate(req.getHistoryEndDate());
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

    public ClientTherapistHandshake updateHandshakeStatus(UUID id, Map<String, String> body) {
        ClientTherapistHandshake handshake = handshakeRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Handshake not found"));
        if (body.containsKey("status") && body.get("status") != null && !body.get("status").isBlank()) {
            String status = body.get("status").toUpperCase();
            handshake.setStatus(status);
            if ("REVOKED".equals(status)) {
                handshake.setRevokedAt(LocalDateTime.now());
            } else if ("ACTIVE".equals(status)) {
                handshake.setGrantedAt(LocalDateTime.now());
                handshake.setRevokedAt(null);
            }
        }
        if (body.containsKey("validFrom")) {
            String val = body.get("validFrom");
            handshake.setValidFrom(val != null && !val.isBlank() ? java.time.LocalDate.parse(val) : null);
        }
        if (body.containsKey("validTo")) {
            String val = body.get("validTo");
            handshake.setValidTo(val != null && !val.isBlank() ? java.time.LocalDate.parse(val) : null);
        }
        if (body.containsKey("historyStartDate")) {
            String val = body.get("historyStartDate");
            handshake.setHistoryStartDate(val != null && !val.isBlank() ? java.time.LocalDate.parse(val) : null);
        }
        if (body.containsKey("historyEndDate")) {
            String val = body.get("historyEndDate");
            handshake.setHistoryEndDate(val != null && !val.isBlank() ? java.time.LocalDate.parse(val) : null);
        }
        return handshakeRepository.save(handshake);
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
