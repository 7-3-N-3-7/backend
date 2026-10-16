package com.platform.controller;

import com.platform.core.controller.BaseController;
import com.platform.crm.repository.JournalEntryRepository;
import com.platform.crm.repository.PsychotraumatologyTestRepository;
import com.platform.entity.*;
import com.platform.repository.*;
import com.platform.service.ClinicPlatformService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/v1")
@CrossOrigin(origins = "*")
public class ClinicPlatformController extends BaseController<ClientProfile, ClientProfile, UUID> {

    private final ClinicPlatformService clinicPlatformService;

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
        this(new ClinicPlatformService(
                clientProfileRepository,
                organisationRepository,
                providerProfileRepository,
                serviceRepository,
                handshakeRepository,
                appointmentRepository,
                journalEntryRepository,
                psychotraumatologyTestRepository
        ));
    }

    @Autowired
    public ClinicPlatformController(ClinicPlatformService clinicPlatformService) {
        super(clinicPlatformService);
        this.clinicPlatformService = clinicPlatformService;
    }

    // =========================================================================
    // 0. SESSION / IDENTITY BOOTSTRAP & OVERVIEW ENDPOINTS (DB-BACKED)
    // =========================================================================

    @GetMapping("/clinic/me")
    public ResponseEntity<Map<String, Object>> getCurrentUserContext(Authentication auth) {
        return ResponseEntity.ok(clinicPlatformService.getCurrentUserContext(auth));
    }

    @GetMapping("/clinic/directory")
    public ResponseEntity<Map<String, Object>> getMarketplaceDirectory() {
        return ResponseEntity.ok(clinicPlatformService.getMarketplaceDirectory());
    }

    @GetMapping("/clinic/client/{clientUuid}/overview")
    public ResponseEntity<Map<String, Object>> getClientOverview(@PathVariable UUID clientUuid) {
        return ResponseEntity.ok(clinicPlatformService.getClientOverview(clientUuid));
    }

    @GetMapping("/clinic/therapist/{therapistUuid}/overview")
    public ResponseEntity<Map<String, Object>> getTherapistOverview(@PathVariable UUID therapistUuid) {
        return ResponseEntity.ok(clinicPlatformService.getTherapistOverview(therapistUuid));
    }

    // =========================================================================
    // 1. CLIENT PROFILE REGISTRATION & SETTINGS (Bilag 5.1.1 & Q12)
    // =========================================================================

    @PostMapping("/public/client-profile")
    public ResponseEntity<ClientProfile> savePublicClientProfile(@RequestBody ClientProfile profile) {
        return clinicPlatformService.savePublicClientProfile(profile);
    }

    @PutMapping("/clinic/client-profile")
    public ResponseEntity<ClientProfile> updateClientProfile(@RequestBody ClientProfile profile) {
        return savePublicClientProfile(profile);
    }

    @GetMapping("/clinic/client-profile/{userUuid}")
    public ResponseEntity<ClientProfile> getClientProfile(@PathVariable UUID userUuid) {
        return clinicPlatformService.getClientProfileByUserId(userUuid)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    // =========================================================================
    // 2. THERAPIST ONBOARDING & CVR ORGANISATIONS (Bilag 5.1.2 & Q2)
    // =========================================================================

    @GetMapping("/clinic/organisations")
    public ResponseEntity<List<Organisation>> getOrganisations() {
        return ResponseEntity.ok(clinicPlatformService.getOrganisations());
    }

    public ResponseEntity<ProviderProfile> onboardTherapist(Map<String, String> payload) {
        return onboardTherapist(payload, null);
    }

    @PostMapping("/clinic/therapist-onboarding")
    public ResponseEntity<ProviderProfile> onboardTherapist(
            @RequestBody Map<String, String> payload,
            Authentication auth
    ) {
        return ResponseEntity.ok(clinicPlatformService.onboardTherapist(payload, auth));
    }

    // =========================================================================
    // 3. HYBRID SERVICE CATALOG & MARKETPLACE DIRECTORY (Bilag 5.1.3, Q7, Q8)
    // =========================================================================

    @GetMapping("/clinic/services")
    public ResponseEntity<List<Service>> getAllServices() {
        return ResponseEntity.ok(clinicPlatformService.getAllServices());
    }

    @PostMapping("/clinic/services")
    public ResponseEntity<Service> createService(
            @RequestBody Map<String, Object> payload,
            Authentication auth
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(clinicPlatformService.createService(payload, auth));
    }

    public ResponseEntity<Service> createService(Map<String, Object> payload) {
        return createService(payload, null);
    }

    @PutMapping("/clinic/services/{id}")
    public ResponseEntity<Service> updateService(
            @PathVariable UUID id,
            @RequestBody Map<String, Object> payload,
            Authentication auth
    ) {
        return ResponseEntity.ok(clinicPlatformService.updateService(id, payload, auth));
    }

    public ResponseEntity<Service> updateService(UUID id, Map<String, Object> payload) {
        return updateService(id, payload, null);
    }

    @DeleteMapping("/clinic/services/{id}")
    public ResponseEntity<Map<String, Object>> deleteService(@PathVariable UUID id) {
        return ResponseEntity.ok(clinicPlatformService.deleteService(id));
    }

    @PatchMapping("/clinic/services/{id}/toggle")
    public ResponseEntity<Service> toggleServiceActive(@PathVariable UUID id) {
        return ResponseEntity.ok(clinicPlatformService.toggleServiceActive(id));
    }

    // =========================================================================
    // 4. CLIENT-THERAPIST HANDSHAKES & CRM ROSTER (Q3, Q4, Q10)
    // =========================================================================

    @GetMapping("/clinic/handshakes/client/{clientUuid}")
    public ResponseEntity<List<ClientTherapistHandshake>> getClientHandshakes(@PathVariable UUID clientUuid) {
        return ResponseEntity.ok(clinicPlatformService.getClientHandshakes(clientUuid));
    }

    @GetMapping("/clinic/handshakes/therapist/{therapistUuid}")
    public ResponseEntity<List<ClientTherapistHandshake>> getTherapistHandshakes(@PathVariable UUID therapistUuid) {
        return ResponseEntity.ok(clinicPlatformService.getTherapistHandshakes(therapistUuid));
    }

    @PostMapping("/clinic/handshakes")
    public ResponseEntity<ClientTherapistHandshake> createOrUpdateHandshake(@RequestBody ClientTherapistHandshake req) {
        return clinicPlatformService.createOrUpdateHandshake(req);
    }

    @PatchMapping({"/clinic/handshakes/{id}/status", "/clinic/handshakes/{id}"})
    @PutMapping("/clinic/handshakes/{id}")
    public ResponseEntity<ClientTherapistHandshake> updateHandshakeStatus(
            @PathVariable UUID id,
            @RequestBody Map<String, String> body
    ) {
        return ResponseEntity.ok(clinicPlatformService.updateHandshakeStatus(id, body));
    }
}
