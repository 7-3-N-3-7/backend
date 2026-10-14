package com.platform.controller;

import com.platform.entity.*;
import com.platform.repository.*;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.*;

@RestController
@RequestMapping("/api/v1")
@CrossOrigin(origins = "*")
public class ClinicPlatformController {

    private final ClientProfileRepository clientProfileRepository;
    private final OrganisationRepository organisationRepository;
    private final ProviderProfileRepository providerProfileRepository;
    private final ServiceRepository serviceRepository;
    private final HandshakeRepository handshakeRepository;

    public ClinicPlatformController(
            ClientProfileRepository clientProfileRepository,
            OrganisationRepository organisationRepository,
            ProviderProfileRepository providerProfileRepository,
            ServiceRepository serviceRepository,
            HandshakeRepository handshakeRepository
    ) {
        this.clientProfileRepository = clientProfileRepository;
        this.organisationRepository = organisationRepository;
        this.providerProfileRepository = providerProfileRepository;
        this.serviceRepository = serviceRepository;
        this.handshakeRepository = handshakeRepository;
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

    @PostMapping("/clinic/therapist-onboarding")
    public ResponseEntity<ProviderProfile> onboardTherapist(@RequestBody Map<String, String> payload) {
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
                ? UUID.fromString(payload.get("userUuid"))
                : UUID.randomUUID();

        ProviderProfile profile = providerProfileRepository.findByUserUuid(userUuid)
                .orElseGet(ProviderProfile::new);
        profile.setUserUuid(userUuid);
        profile.setDisplayName(payload.getOrDefault("displayName", "Aut. Psykoterapeut"));
        profile.setOrganisation(organisation);
        profile.setBiography(payload.getOrDefault("biography", ""));
        profile.setWorkingHoursJson(payload.getOrDefault("workingHoursJson", "{\"mon_fri\":\"09:00-16:00\"}"));

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
        Service service = new Service();
        service.setName(String.valueOf(payload.getOrDefault("name", "Therapy Session")));
        service.setDescription(String.valueOf(payload.getOrDefault("description", "")));
        service.setDurationMinutes(Integer.parseInt(String.valueOf(payload.getOrDefault("durationMinutes", "60"))));
        service.setPrice(Double.parseDouble(String.valueOf(payload.getOrDefault("price", "850.0"))));
        service.setLocation(String.valueOf(payload.getOrDefault("location", "Clinic")));
        service.setIsActive(true);

        if (payload.get("providerProfileId") != null) {
            UUID providerId = UUID.fromString(payload.get("providerProfileId").toString());
            providerProfileRepository.findById(providerId).ifPresent(service::setProvider);
        }
        if (payload.get("organisationId") != null) {
            UUID orgId = UUID.fromString(payload.get("organisationId").toString());
            organisationRepository.findById(orgId).ifPresent(service::setOrganisation);
        }

        return ResponseEntity.status(HttpStatus.CREATED).body(serviceRepository.save(service));
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
        Optional<ClientTherapistHandshake> existing = handshakeRepository.findByClientUuidAndTherapistUuid(
                req.getClientUuid(), req.getTherapistUuid()
        );
        if (existing.isPresent()) {
            ClientTherapistHandshake current = existing.get();
            current.setStatus(req.getStatus() != null ? req.getStatus() : "ACTIVE");
            if ("ACTIVE".equalsIgnoreCase(current.getStatus())) {
                current.setGrantedAt(LocalDateTime.now());
                current.setRevokedAt(null);
            }
            return ResponseEntity.ok(handshakeRepository.save(current));
        }
        if (req.getStatus() == null) {
            req.setStatus("ACTIVE");
        }
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
}
