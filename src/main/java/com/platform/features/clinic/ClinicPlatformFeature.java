package com.platform.features.clinic;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.platform.core.BaseDomainCore.BaseRepository;
import com.platform.core.BaseDomainCore.BaseService;
import com.platform.core.BaseDomainCore.ErrorCode;
import com.platform.core.BaseDomainCore.HasId;
import com.platform.core.BaseDomainCore.Identifiable;
import com.platform.core.BaseWebCore.BaseController;
import com.platform.features.appointment.AppointmentFeature.Appointment;
import com.platform.features.appointment.AppointmentFeature.AppointmentPort;
import com.platform.features.journal.JournalFeature.JournalEntry;
import com.platform.features.journal.JournalFeature.JournalPort;
import com.platform.features.psychotraumatology.PsychotraumatologyFeature.PsychotraumatologyPort;
import com.platform.features.psychotraumatology.PsychotraumatologyFeature.PsychotraumatologyTest;
import com.platform.features.user.UserFeature.UserEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.validation.Valid;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Currency;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * ============================================================================
 * ARKITEKTONISK VISITKORT: ClinicPlatformFeature (Vertical Feature Slice)
 * ============================================================================
 * @Spejler: frontend/src/features/clinic/ClinicPlatformFeature.tsx
 *           frontend/src/features/management/ManagementFeature.tsx
 *
 * FORMÅL (Til Censor, Vejleder & Nykommende Udviklere):
 * Udgør det organisatoriske og juridiske omdrejningspunkt i platformen efter
 * 5-trins Vertical Slice-strukturen:
 *
 * ARKITEKTONISK OVERBLIK (5 TRIN):
 *   1. [Record/DTO: ClientProfileDto], [Record/DTO: OrganisationDto],
 *      [Record/DTO: ProviderProfileDto], [Record/DTO: ServiceDto] &
 *      [Record/DTO: ClientTherapistHandshakeDto]
 *      Java 17 `record` kontrakter der implementerer [Interface: HasId].
 *   2. [Entity: ClientProfile], [Entity: Organisation], [Entity: ProviderProfile],
 *      [Entity: Service] & [Entity: ClientTherapistHandshake]
 *      JPA-entiteter med CVR-validering, ISO 4217-valutakontrol og granulært
 *      samtykke pr. JGit-gren (`allowedBranches`, `NO_MOOD_SCORES`, `NO_PCL5_SCORES`).
 *   3. [Interface: ProviderProfilePort], [Interface: HandshakePort] & Package-Private Repositories
 *      Smalle port-interfaces udstilles til andre slices (`AppointmentFeature`,
 *      `JournalFeature`), mens alle 5 Spring Data JPA-repositories forbliver
 *      package-private (Information Hiding).
 *   4. [Service: ClinicPlatformService]
 *      Arver fra [AbstractClass: BaseService<ClientProfile, ClientProfileDto>] og
 *      håndhæver profil-komplethedstjek (CVR, adresse, arbejdstid, IBAN) samt
 *      GDPR Art. 15/20 dataeksport og Art. 17 sletning.
 *   5. [Controller: ClinicPlatformController]
 *      Arver standard CRUD fra [AbstractClass: BaseController<ClientProfile, ClientProfileDto>]
 *      og udstiller klinik-, onboarding-, handshake- og GDPR-endpoints under `/api/v1/clinic`.
 * ============================================================================
 */
public final class ClinicPlatformFeature {

    private ClinicPlatformFeature() {}

    // =========================================================================
    // 1. DTO CONTRACTS (Public Java Records implementing HasId)
    // =========================================================================

    public record ClientProfileDto(
            UUID id,
            UUID userId,
            String fullName,
            String dateOfBirth,
            String homeAddress,
            String postalCode,
            @JsonAlias("phone") String phoneNumber,
            String email
    ) implements HasId {}

    public record OrganisationDto(
            UUID id,
            String cvrNumber,
            String name,
            String address,
            String postalCode,
            String phoneNumber,
            String email,
            String videoMeetingUrl,
            String iban,
            String currency
    ) implements HasId {
        public static OrganisationDto fromEntity(Organisation org) {
            return new OrganisationDto(
                    org.getId(),
                    org.getCvrNumber(),
                    org.getName(),
                    org.getAddress(),
                    org.getPostalCode(),
                    org.getPhoneNumber(),
                    org.getEmail(),
                    org.getVideoMeetingUrl(),
                    org.getIban(),
                    org.getCurrency()
            );
        }
    }

    public record ProviderProfileDto(
            UUID id,
            UUID userUuid,
            String displayName,
            UUID organisationId,
            String organisationName,
            String biography,
            String workingHoursJson,
            String videoMeetingUrl,
            String iban,
            String currency
    ) implements HasId {
        public static ProviderProfileDto fromEntity(ProviderProfile profile) {
            Organisation org = profile.getOrganisation();
            return new ProviderProfileDto(
                    profile.getId(),
                    profile.getUserUuid(),
                    profile.getDisplayName(),
                    org != null ? org.getId() : null,
                    org != null ? org.getName() : null,
                    profile.getBiography(),
                    profile.getWorkingHoursJson(),
                    profile.getVideoMeetingUrl(),
                    profile.getIban(),
                    profile.getCurrency()
            );
        }
    }

    public record ServiceDto(
            UUID id,
            UUID providerProfileId,
            UUID organisationId,
            String name,
            String description,
            Integer durationMinutes,
            Double price,
            String location,
            String currency,
            Boolean isActive
    ) implements HasId {
        public static ServiceDto fromEntity(Service svc) {
            return new ServiceDto(
                    svc.getId(),
                    svc.getProvider() != null ? svc.getProvider().getId() : null,
                    svc.getOrganisation() != null ? svc.getOrganisation().getId() : null,
                    svc.getName(),
                    svc.getDescription(),
                    svc.getDurationMinutes(),
                    svc.getPrice(),
                    svc.getLocation(),
                    svc.getCurrency(),
                    svc.getIsActive()
            );
        }
    }

    public record ClientTherapistHandshakeDto(
            UUID id,
            UUID clientUuid,
            String clientName,
            UUID therapistUuid,
            String therapistName,
            String organisationName,
            String status,
            LocalDateTime grantedAt,
            LocalDateTime revokedAt,
            LocalDate validFrom,
            LocalDate validTo,
            LocalDate historyStartDate,
            LocalDate historyEndDate,
            String allowedBranches,
            String customRestrictions,
            String branchRestrictions
    ) implements HasId {
        public static ClientTherapistHandshakeDto fromEntity(ClientTherapistHandshake hs) {
            return new ClientTherapistHandshakeDto(
                    hs.getId(),
                    hs.getClientUuid(),
                    hs.getClientName(),
                    hs.getTherapistUuid(),
                    hs.getTherapistName(),
                    hs.getOrganisationName(),
                    hs.getStatus(),
                    hs.getGrantedAt(),
                    hs.getRevokedAt(),
                    hs.getValidFrom(),
                    hs.getValidTo(),
                    hs.getHistoryStartDate(),
                    hs.getHistoryEndDate(),
                    hs.getAllowedBranches(),
                    hs.getCustomRestrictions(),
                    hs.getBranchRestrictions()
            );
        }
    }

    public record OnboardingRequestDto(
            UUID id,
            UUID userUuid,
            String displayName,
            String cvrNumber,
            String organisationName,
            String biography,
            String workingHoursJson,
            String iban,
            String currency
    ) implements HasId {}

    public record HandshakeRequestDto(
            UUID id,
            UUID clientUuid,
            String clientName,
            UUID therapistUuid,
            String therapistName,
            String organisationName,
            String status
    ) implements HasId {}

    // =========================================================================
    // 2. JPA / MONGO ENTITIES (Implements Identifiable)
    // =========================================================================

    @Entity(name = "ClientProfile")
    @Table(name = "client_profiles")
    public static class ClientProfile implements Identifiable {

        @Id
        @GeneratedValue(strategy = GenerationType.UUID)
        private UUID id;

        @Column(name = "user_id", unique = true, nullable = false)
        private UUID userId;

        @Column(name = "full_name", nullable = false)
        private String fullName;

        @Column(name = "date_of_birth", nullable = false)
        private String dateOfBirth;

        @Column(name = "home_address", nullable = false)
        private String homeAddress;

        @Column(name = "postal_code", nullable = false)
        private String postalCode;

        @Column(name = "phone_number", nullable = false)
        private String phoneNumber;

        @Column
        private String email;

        public ClientProfile() {}

        public ClientProfile(UUID userId, String fullName, String dateOfBirth, String homeAddress, String postalCode, String phoneNumber, String email) {
            this.userId = userId;
            this.fullName = fullName;
            this.dateOfBirth = dateOfBirth;
            this.homeAddress = homeAddress;
            this.postalCode = postalCode;
            this.phoneNumber = phoneNumber;
            this.email = email;
        }

        @Override public UUID getId() { return id; }
        @Override public void setId(UUID id) { this.id = id; }

        public UUID getUserId() { return userId; }
        public void setUserId(UUID userId) { this.userId = userId; }

        public String getFullName() { return fullName; }
        public void setFullName(String fullName) { this.fullName = fullName; }

        public String getDateOfBirth() { return dateOfBirth; }
        public void setDateOfBirth(String dateOfBirth) { this.dateOfBirth = dateOfBirth; }

        public String getHomeAddress() { return homeAddress; }
        public void setHomeAddress(String homeAddress) { this.homeAddress = homeAddress; }

        public String getPostalCode() { return postalCode; }
        public void setPostalCode(String postalCode) { this.postalCode = postalCode; }

        public String getPhoneNumber() { return phoneNumber; }
        public void setPhoneNumber(String phoneNumber) { this.phoneNumber = phoneNumber; }

        public String getEmail() { return email; }
        public void setEmail(String email) { this.email = email; }
    }

    @Entity(name = "Organisation")
    @Table(name = "organisations")
    public static class Organisation implements Identifiable {

        @Id
        @GeneratedValue(strategy = GenerationType.UUID)
        private UUID id;

        @Column(name = "cvr_number", unique = true, nullable = false, length = 20)
        private String cvrNumber;

        @Column(nullable = false)
        private String name;

        @Column(name = "address")
        private String address;

        @Column(name = "postal_code")
        private String postalCode;

        @Column(name = "phone_number")
        private String phoneNumber;

        @Column(name = "email")
        private String email;

        @Column(name = "video_meeting_url", length = 500)
        private String videoMeetingUrl;

        @Column(name = "iban", length = 64)
        private String iban;

        @Column(name = "currency", length = 10)
        private String currency = "DKK";

        public Organisation() {}

        public Organisation(String cvrNumber, String name) {
            this.cvrNumber = cvrNumber;
            this.name = name;
        }

        @Override public UUID getId() { return id; }
        @Override public void setId(UUID id) { this.id = id; }

        public String getCvrNumber() { return cvrNumber; }
        public void setCvrNumber(String cvrNumber) { this.cvrNumber = cvrNumber; }

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }

        public String getAddress() { return address; }
        public void setAddress(String address) { this.address = address; }

        public String getPostalCode() { return postalCode; }
        public void setPostalCode(String postalCode) { this.postalCode = postalCode; }

        public String getPhoneNumber() { return phoneNumber; }
        public void setPhoneNumber(String phoneNumber) { this.phoneNumber = phoneNumber; }

        public String getEmail() { return email; }
        public void setEmail(String email) { this.email = email; }

        public String getVideoMeetingUrl() { return videoMeetingUrl; }
        public void setVideoMeetingUrl(String videoMeetingUrl) { this.videoMeetingUrl = videoMeetingUrl; }

        @JsonIgnore
        public Optional<String> findVideoMeetingUrl() {
            return Optional.ofNullable(videoMeetingUrl).map(String::trim).filter(s -> !s.isEmpty());
        }

        public String getIban() { return iban; }
        public void setIban(String iban) { this.iban = iban; }

        @JsonIgnore
        public Optional<String> findIban() {
            return Optional.ofNullable(iban).map(String::trim).filter(s -> !s.isEmpty());
        }

        public String getCurrency() {
            return Optional.ofNullable(currency)
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .orElse("DKK");
        }

        public void setCurrency(String currency) {
            this.currency = Optional.ofNullable(currency)
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .map(String::toUpperCase)
                    .map(c -> Currency.getInstance(c).getCurrencyCode())
                    .orElse("DKK");
        }

        public Currency resolveJavaCurrency() {
            return Currency.getInstance(getCurrency());
        }
    }

    @Entity(name = "ProviderProfile")
    @Table(name = "provider_profiles")
    public static class ProviderProfile implements Identifiable {

        @Id
        @GeneratedValue(strategy = GenerationType.UUID)
        private UUID id;

        @OneToOne
        @JoinColumn(name = "user_id", referencedColumnName = "id", nullable = true)
        private UserEntity user;

        @Column(name = "user_uuid")
        private UUID userUuid;

        @Column(name = "display_name")
        private String displayName;

        @ManyToOne
        @JoinColumn(name = "organisation_id", nullable = true)
        private Organisation organisation;

        @Column(columnDefinition = "TEXT")
        private String biography;

        @Column(name = "working_hours_json", columnDefinition = "TEXT")
        private String workingHoursJson;

        @Column(name = "video_meeting_url", length = 500)
        private String videoMeetingUrl;

        @Column(name = "iban", length = 64)
        private String iban;

        @Column(name = "currency", length = 10)
        private String currency = "DKK";

        public ProviderProfile() {}

        @Override public UUID getId() { return id; }
        @Override public void setId(UUID id) { this.id = id; }

        public UserEntity getUser() { return user; }
        public void setUser(UserEntity user) { this.user = user; }

        public UUID getUserUuid() { return userUuid; }
        public void setUserUuid(UUID userUuid) { this.userUuid = userUuid; }

        public String getDisplayName() { return displayName; }
        public void setDisplayName(String displayName) { this.displayName = displayName; }

        public Organisation getOrganisation() { return organisation; }
        public void setOrganisation(Organisation organisation) { this.organisation = organisation; }

        @JsonIgnore
        public Optional<Organisation> findOrganisation() {
            return Optional.ofNullable(organisation);
        }

        public String getBiography() { return biography; }
        public void setBiography(String biography) { this.biography = biography; }

        public String getWorkingHoursJson() { return workingHoursJson; }
        public void setWorkingHoursJson(String workingHoursJson) { this.workingHoursJson = workingHoursJson; }

        public String getVideoMeetingUrl() { return videoMeetingUrl; }
        public void setVideoMeetingUrl(String videoMeetingUrl) { this.videoMeetingUrl = videoMeetingUrl; }

        @JsonIgnore
        public Optional<String> findVideoMeetingUrl() {
            return Optional.ofNullable(videoMeetingUrl).map(String::trim).filter(s -> !s.isEmpty());
        }

        public String getIban() { return iban; }
        public void setIban(String iban) { this.iban = iban; }

        @JsonIgnore
        public Optional<String> findIban() {
            return Optional.ofNullable(iban).map(String::trim).filter(s -> !s.isEmpty());
        }

        public String getCurrency() {
            return Optional.ofNullable(currency)
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .orElse("DKK");
        }

        public void setCurrency(String currency) {
            this.currency = Optional.ofNullable(currency)
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .map(String::toUpperCase)
                    .map(c -> Currency.getInstance(c).getCurrencyCode())
                    .orElse("DKK");
        }

        public Currency resolveJavaCurrency() {
            return Currency.getInstance(getCurrency());
        }
    }

    @Entity(name = "Service")
    @Table(name = "services")
    public static class Service implements Identifiable {

        @Id
        @GeneratedValue(strategy = GenerationType.UUID)
        private UUID id;

        @ManyToOne
        @JoinColumn(name = "provider_profile_id", nullable = true)
        private ProviderProfile provider;

        @ManyToOne
        @JoinColumn(name = "organisation_id", nullable = true)
        private Organisation organisation;

        private String name;

        @Column(columnDefinition = "TEXT")
        private String description;

        private Integer durationMinutes;
        private Double price;
        private String location;

        @Column(name = "currency", length = 10)
        private String currency = "DKK";

        @Column(name = "is_active")
        private Boolean isActive = true;

        public Service() {}

        @Override public UUID getId() { return id; }
        @Override public void setId(UUID id) { this.id = id; }

        public ProviderProfile getProvider() { return provider; }
        public void setProvider(ProviderProfile provider) { this.provider = provider; }

        @JsonIgnore
        public Optional<ProviderProfile> findProvider() {
            return Optional.ofNullable(provider);
        }

        public Organisation getOrganisation() { return organisation; }
        public void setOrganisation(Organisation organisation) { this.organisation = organisation; }

        @JsonIgnore
        public Optional<Organisation> findOrganisation() {
            return Optional.ofNullable(organisation);
        }

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }

        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }

        public Integer getDurationMinutes() { return durationMinutes; }
        public void setDurationMinutes(Integer durationMinutes) { this.durationMinutes = durationMinutes; }

        public Double getPrice() { return price; }
        public void setPrice(Double price) { this.price = price; }

        public String getLocation() { return location; }
        public void setLocation(String location) { this.location = location; }

        public Boolean getIsActive() { return isActive != null ? isActive : true; }
        public void setIsActive(Boolean isActive) { this.isActive = isActive; }

        public String getCurrency() {
            return Optional.ofNullable(currency)
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .orElse("DKK");
        }

        public void setCurrency(String currency) {
            this.currency = Optional.ofNullable(currency)
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .map(String::toUpperCase)
                    .map(c -> Currency.getInstance(c).getCurrencyCode())
                    .orElse("DKK");
        }

        public Currency resolveJavaCurrency() {
            return Currency.getInstance(getCurrency());
        }
    }

    @Entity(name = "ClientTherapistHandshake")
    @Table(name = "client_therapist_handshakes")
    public static class ClientTherapistHandshake implements Identifiable {

        @Id
        @GeneratedValue(strategy = GenerationType.UUID)
        private UUID id;

        @Column(name = "client_uuid", nullable = false)
        private UUID clientUuid;

        @Column(name = "client_name")
        private String clientName;

        @Column(name = "therapist_uuid", nullable = false)
        private UUID therapistUuid;

        @Column(name = "therapist_name")
        private String therapistName;

        @Column(name = "organisation_name")
        private String organisationName;

        @Column(nullable = false)
        private String status;

        @Column(name = "granted_at")
        private LocalDateTime grantedAt;

        @Column(name = "revoked_at")
        private LocalDateTime revokedAt;

        @Column(name = "valid_from")
        private LocalDate validFrom;

        @Column(name = "valid_to")
        private LocalDate validTo;

        @Column(name = "history_start_date")
        private LocalDate historyStartDate;

        @Column(name = "history_end_date")
        private LocalDate historyEndDate;

        @Column(name = "allowed_branches", columnDefinition = "TEXT")
        private String allowedBranches;

        @Column(name = "custom_restrictions", columnDefinition = "TEXT")
        private String customRestrictions;

        @Column(name = "branch_restrictions", columnDefinition = "TEXT")
        private String branchRestrictions;

        public ClientTherapistHandshake() {}

        public ClientTherapistHandshake(UUID clientUuid, String clientName, UUID therapistUuid, String therapistName, String organisationName, String status) {
            this.clientUuid = clientUuid;
            this.clientName = clientName;
            this.therapistUuid = therapistUuid;
            this.therapistName = therapistName;
            this.organisationName = organisationName;
            this.status = status;
            if ("ACTIVE".equals(status)) {
                this.grantedAt = LocalDateTime.now();
            }
        }

        @Override public UUID getId() { return id; }
        @Override public void setId(UUID id) { this.id = id; }

        public UUID getClientUuid() { return clientUuid; }
        public void setClientUuid(UUID clientUuid) { this.clientUuid = clientUuid; }

        public String getClientName() { return clientName; }
        public void setClientName(String clientName) { this.clientName = clientName; }

        @JsonIgnore
        public Optional<String> findClientName() {
            return Optional.ofNullable(clientName).map(String::trim).filter(Predicate.not(String::isEmpty));
        }

        public UUID getTherapistUuid() { return therapistUuid; }
        public void setTherapistUuid(UUID therapistUuid) { this.therapistUuid = therapistUuid; }

        public String getTherapistName() { return therapistName; }
        public void setTherapistName(String therapistName) { this.therapistName = therapistName; }

        @JsonIgnore
        public Optional<String> findTherapistName() {
            return Optional.ofNullable(therapistName).map(String::trim).filter(Predicate.not(String::isEmpty));
        }

        public String getOrganisationName() { return organisationName; }
        public void setOrganisationName(String organisationName) { this.organisationName = organisationName; }

        @JsonIgnore
        public Optional<String> findOrganisationName() {
            return Optional.ofNullable(organisationName).map(String::trim).filter(Predicate.not(String::isEmpty));
        }

        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }

        public LocalDateTime getGrantedAt() { return grantedAt; }
        public void setGrantedAt(LocalDateTime grantedAt) { this.grantedAt = grantedAt; }

        @JsonIgnore
        public Optional<LocalDateTime> findGrantedAt() {
            return Optional.ofNullable(grantedAt);
        }

        public LocalDateTime getRevokedAt() { return revokedAt; }
        public void setRevokedAt(LocalDateTime revokedAt) { this.revokedAt = revokedAt; }

        @JsonIgnore
        public Optional<LocalDateTime> findRevokedAt() {
            return Optional.ofNullable(revokedAt);
        }

        public LocalDate getValidFrom() { return validFrom; }
        public void setValidFrom(LocalDate validFrom) { this.validFrom = validFrom; }

        @JsonIgnore
        public Optional<LocalDate> findValidFrom() {
            return Optional.ofNullable(validFrom);
        }

        public LocalDate getValidTo() { return validTo; }
        public void setValidTo(LocalDate validTo) { this.validTo = validTo; }

        @JsonIgnore
        public Optional<LocalDate> findValidTo() {
            return Optional.ofNullable(validTo);
        }

        public LocalDate getHistoryStartDate() { return historyStartDate; }
        public void setHistoryStartDate(LocalDate historyStartDate) { this.historyStartDate = historyStartDate; }

        @JsonIgnore
        public Optional<LocalDate> findHistoryStartDate() {
            return Optional.ofNullable(historyStartDate);
        }

        public LocalDate getHistoryEndDate() { return historyEndDate; }
        public void setHistoryEndDate(LocalDate historyEndDate) { this.historyEndDate = historyEndDate; }

        @JsonIgnore
        public Optional<LocalDate> findHistoryEndDate() {
            return Optional.ofNullable(historyEndDate);
        }

        public String getAllowedBranches() { return allowedBranches; }
        public void setAllowedBranches(String allowedBranches) { this.allowedBranches = allowedBranches; }

        @JsonIgnore
        public Optional<String> findAllowedBranches() {
            return Optional.ofNullable(allowedBranches).map(String::trim).filter(Predicate.not(String::isEmpty));
        }

        public String getCustomRestrictions() { return customRestrictions; }
        public void setCustomRestrictions(String customRestrictions) { this.customRestrictions = customRestrictions; }

        @JsonIgnore
        public Optional<String> findCustomRestrictions() {
            return Optional.ofNullable(customRestrictions).map(String::trim).filter(Predicate.not(String::isEmpty));
        }

        public String getBranchRestrictions() { return branchRestrictions; }
        public void setBranchRestrictions(String branchRestrictions) { this.branchRestrictions = branchRestrictions; }

        @JsonIgnore
        public Optional<String> findBranchRestrictions() {
            return Optional.ofNullable(branchRestrictions).map(String::trim).filter(Predicate.not(String::isEmpty));
        }

        public Map<String, List<String>> getBranchRestrictionsMap() {
            return findBranchRestrictions()
                    .map(raw -> {
                        Map<String, List<String>> map = new LinkedHashMap<>();
                        for (String line : raw.split("[;\\r\\n]+")) {
                            String trimmedLine = line.trim();
                            int colonIdx = trimmedLine.indexOf(':');
                            if (colonIdx > 0) {
                                String branch = trimmedLine.substring(0, colonIdx).trim();
                                List<String> restrictions = Arrays.stream(trimmedLine.substring(colonIdx + 1).split(","))
                                        .map(String::trim)
                                        .filter(Predicate.not(String::isEmpty))
                                        .collect(Collectors.toList());
                                if (!restrictions.isEmpty()) {
                                    map.put(branch, restrictions);
                                }
                            }
                        }
                        return map;
                    })
                    .orElseGet(LinkedHashMap::new);
        }

        public List<String> getAllowedBranchesList() {
            Set<String> set = new LinkedHashSet<>();
            findAllowedBranches().ifPresent(raw ->
                    Arrays.stream(raw.split(","))
                            .map(String::trim)
                            .filter(Predicate.not(String::isEmpty))
                            .forEach(set::add)
            );
            set.addAll(getBranchRestrictionsMap().keySet());
            return new ArrayList<>(set);
        }

        public boolean isBranchAllowed(String branchName) {
            List<String> allowed = getAllowedBranchesList();
            if (allowed.isEmpty()) {
                return true;
            }
            String effectiveBranch = Optional.ofNullable(branchName)
                    .map(String::trim)
                    .filter(Predicate.not(String::isEmpty))
                    .orElse("main");
            return allowed.contains(effectiveBranch);
        }

        public List<String> getCustomRestrictionsList() {
            return findCustomRestrictions()
                    .map(raw -> Arrays.stream(raw.split(","))
                            .map(String::trim)
                            .filter(Predicate.not(String::isEmpty))
                            .collect(Collectors.toList()))
                    .orElseGet(List::of);
        }

        public List<String> getRestrictionsForBranch(String branchName) {
            return Optional.ofNullable(branchName)
                    .map(String::trim)
                    .filter(Predicate.not(String::isEmpty))
                    .map(effectiveBranch -> {
                        Map<String, List<String>> branchMap = getBranchRestrictionsMap();
                        return branchMap.isEmpty()
                                ? getCustomRestrictionsList()
                                : branchMap.getOrDefault(effectiveBranch, List.of());
                    })
                    .orElseGet(List::of);
        }

        public boolean hasRestrictionForBranch(String branchName, String restrictionCode) {
            return Optional.ofNullable(restrictionCode)
                    .map(String::trim)
                    .filter(Predicate.not(String::isEmpty))
                    .map(code -> getRestrictionsForBranch(branchName).contains(code))
                    .orElse(false);
        }

        public boolean hasCustomRestriction(String restrictionCode) {
            return Optional.ofNullable(restrictionCode)
                    .map(String::trim)
                    .filter(Predicate.not(String::isEmpty))
                    .map(code -> getCustomRestrictionsList().contains(code)
                            || getBranchRestrictionsMap().values().stream().anyMatch(list -> list.contains(code)))
                    .orElse(false);
        }

        public boolean isCurrentlyValid() {
            if (!"ACTIVE".equalsIgnoreCase(status)) {
                return false;
            }
            LocalDate today = LocalDate.now();
            return findValidFrom().map(from -> !today.isBefore(from)).orElse(true)
                    && findValidTo().map(to -> !today.isAfter(to)).orElse(true);
        }

        public boolean isCommitInHistoricalRange(Instant commitTime) {
            return Optional.ofNullable(commitTime)
                    .map(time -> time.atZone(ZoneId.systemDefault()).toLocalDate())
                    .map(commitDate -> findHistoryStartDate().map(start -> !commitDate.isBefore(start)).orElse(true)
                            && findHistoryEndDate().map(end -> !commitDate.isAfter(end)).orElse(true))
                    .orElse(true);
        }
    }

    // =========================================================================
    // 3. REPOSITORIES & PORTS (Package-Private Repositories + Public Ports)
    // =========================================================================

    public interface ProviderProfilePort {
        Optional<ProviderProfile> findByUserUuid(UUID userUuid);
    }

    public interface HandshakePort {
        Optional<ClientTherapistHandshake> findByClientUuidAndTherapistUuid(UUID clientUuid, UUID therapistUuid);
    }

    public interface ServiceLookupPort {
        Optional<Service> findServiceById(UUID id);
    }

    @Repository
    interface ClientProfileRepository extends BaseRepository<ClientProfile> {
        Optional<ClientProfile> findByUserId(UUID userId);
    }

    @Repository
    interface OrganisationRepository extends BaseRepository<Organisation> {
        Optional<Organisation> findByCvrNumber(String cvrNumber);
    }

    @Repository
    interface ProviderProfileRepository extends BaseRepository<ProviderProfile>, ProviderProfilePort {
        @Override
        Optional<ProviderProfile> findByUserUuid(UUID userUuid);
    }

    @Repository
    interface ServiceRepository extends BaseRepository<Service>, ServiceLookupPort {
        List<Service> findByProviderId(UUID providerId);
        List<Service> findByOrganisationId(UUID organisationId);

        @Override
        default Optional<Service> findServiceById(UUID id) {
            return findById(id);
        }
    }

    @Repository
    interface HandshakeRepository extends BaseRepository<ClientTherapistHandshake>, HandshakePort {
        List<ClientTherapistHandshake> findByClientUuid(UUID clientUuid);
        List<ClientTherapistHandshake> findByTherapistUuid(UUID therapistUuid);

        @Override
        Optional<ClientTherapistHandshake> findByClientUuidAndTherapistUuid(UUID clientUuid, UUID therapistUuid);
    }

    // =========================================================================
    // 4. DOMAIN SERVICES (Package-Private)
    // =========================================================================

    @org.springframework.stereotype.Service
    static class ClinicPlatformService extends BaseService<ClientProfile, ClientProfileDto> {

        private final ClientProfileRepository clientProfileRepository;
        private final OrganisationRepository organisationRepository;
        private final ProviderProfileRepository providerProfileRepository;
        private final ServiceRepository serviceRepository;
        private final HandshakeRepository handshakeRepository;
        private final AppointmentPort appointmentRepository;
        private final JournalPort journalEntryRepository;
        private final PsychotraumatologyPort psychotraumatologyTestRepository;

        @Autowired
        ClinicPlatformService(
                ClientProfileRepository clientProfileRepository,
                OrganisationRepository organisationRepository,
                ProviderProfileRepository providerProfileRepository,
                ServiceRepository serviceRepository,
                HandshakeRepository handshakeRepository,
                @Autowired(required = false) AppointmentPort appointmentRepository,
                @Autowired(required = false) JournalPort journalEntryRepository,
                @Autowired(required = false) PsychotraumatologyPort psychotraumatologyTestRepository
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

        ClinicPlatformService(
                ClientProfileRepository clientProfileRepository,
                OrganisationRepository organisationRepository,
                ProviderProfileRepository providerProfileRepository,
                ServiceRepository serviceRepository,
                HandshakeRepository handshakeRepository
        ) {
            this(clientProfileRepository, organisationRepository, providerProfileRepository, serviceRepository, handshakeRepository, null, null, null);
        }

        @Override
        protected ClientProfileDto toDto(ClientProfile clientProfileEntity) {
            if (clientProfileEntity == null) {
                return null;
            }
            return new ClientProfileDto(
                    clientProfileEntity.getId(),
                    clientProfileEntity.getUserId(),
                    clientProfileEntity.getFullName(),
                    clientProfileEntity.getDateOfBirth(),
                    clientProfileEntity.getHomeAddress(),
                    clientProfileEntity.getPostalCode(),
                    clientProfileEntity.getPhoneNumber(),
                    clientProfileEntity.getEmail()
            );
        }

        @Override
        protected ClientProfile toEntity(ClientProfileDto clientProfileDto) {
            if (clientProfileDto == null) {
                return null;
            }
            ClientProfile clientProfileEntity = new ClientProfile(
                    Objects.requireNonNullElseGet(clientProfileDto.userId(), UUID::randomUUID),
                    Objects.requireNonNullElse(clientProfileDto.fullName(), ""),
                    Objects.requireNonNullElse(clientProfileDto.dateOfBirth(), ""),
                    Objects.requireNonNullElse(clientProfileDto.homeAddress(), ""),
                    Objects.requireNonNullElse(clientProfileDto.postalCode(), ""),
                    Objects.requireNonNullElse(clientProfileDto.phoneNumber(), ""),
                    clientProfileDto.email()
            );
            clientProfileEntity.setId(clientProfileDto.id());
            return clientProfileEntity;
        }

        @Override
        protected void validateBeforeCreate(ClientProfileDto dto) {
            ErrorCode.BAD_REQUEST.require(
                    dto != null && dto.fullName() != null && !dto.fullName().isBlank(),
                    "Client full name is required"
            );
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
            List<Service> services = serviceRepository.findAll();

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
                String effectiveLocation = apt.getLocation();
                if (effectiveLocation == null || effectiveLocation.isBlank()) {
                    effectiveLocation = srv != null ? srv.getLocation() : null;
                }
                String videoMeetingUrl = Optional.ofNullable(tp)
                        .flatMap(ProviderProfile::findVideoMeetingUrl)
                        .or(() -> Optional.ofNullable(tp).flatMap(ProviderProfile::findOrganisation).flatMap(Organisation::findVideoMeetingUrl))
                        .orElse(null);
                if (effectiveLocation != null && (effectiveLocation.toLowerCase().contains("online") || effectiveLocation.toLowerCase().contains("video"))) {
                    if (videoMeetingUrl != null) {
                        effectiveLocation = "Online: " + videoMeetingUrl;
                    }
                }
                item.put("price", srv != null ? srv.getPrice() : null);
                item.put("currency", srv != null ? srv.getCurrency() : (tp != null ? tp.getCurrency() : "DKK"));
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
                item.put("allowedBranches", hs.getAllowedBranches());
                item.put("allowedBranchesList", hs.getAllowedBranchesList());
                item.put("customRestrictions", hs.getCustomRestrictions());
                item.put("customRestrictionsList", hs.getCustomRestrictionsList());
                item.put("isCurrentlyValid", hs.isCurrentlyValid());
                enrichedHandshakes.add(item);
            }

            List<JournalEntry> journals = journalEntryRepository != null
                    ? journalEntryRepository.findByClientUuidOrderByTimestampDesc(clientUuid)
                    : List.of();

            List<PsychotraumatologyTest> tests = psychotraumatologyTestRepository != null
                    ? psychotraumatologyTestRepository.findByClientUuid(clientUuid)
                    : List.of();

            List<Service> marketplaceServices = providerProfile == null
                    ? allServices
                    : allServices.stream()
                            .filter(s -> !isTherapistService(s, providerProfile))
                            .collect(Collectors.toList());

            List<ProviderProfile> visibleProviders = allProviders.stream()
                    .filter(p -> p.getUserUuid() == null || !Objects.equals(clientUuid, p.getUserUuid()))
                    .collect(Collectors.toList());

            Map<String, Object> response = new LinkedHashMap<>();
            response.put("clientProfile", clientProfile);
            response.put("providerProfile", providerProfile);
            response.put("appointments", enrichedAppointments);
            response.put("handshakes", enrichedHandshakes);
            response.put("journals", journals);
            response.put("pcl5Tests", tests);
            response.put("providers", visibleProviders);
            response.put("services", marketplaceServices);
            return response;
        }

        public Map<String, Object> getTherapistOverview(UUID therapistUuid) {
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
                String effectiveLocation = apt.getLocation();
                if (effectiveLocation == null || effectiveLocation.isBlank()) {
                    effectiveLocation = srv != null ? srv.getLocation() : null;
                }
                if (effectiveLocation != null && (effectiveLocation.toLowerCase().contains("klient") || effectiveLocation.toLowerCase().contains("client") || effectiveLocation.toLowerCase().contains("hjemmebesøg"))) {
                    if (cp != null && cp.getHomeAddress() != null && !cp.getHomeAddress().isBlank()) {
                        effectiveLocation = "Hos klienten: " + cp.getHomeAddress().trim() + (cp.getPostalCode() != null && !cp.getPostalCode().isBlank() ? ", " + cp.getPostalCode().trim() : "");
                    }
                }

                String videoMeetingUrl = Optional.ofNullable(provider)
                        .flatMap(ProviderProfile::findVideoMeetingUrl)
                        .or(() -> Optional.ofNullable(provider).flatMap(ProviderProfile::findOrganisation).flatMap(Organisation::findVideoMeetingUrl))
                        .orElse(null);
                if (effectiveLocation != null && (effectiveLocation.toLowerCase().contains("online") || effectiveLocation.toLowerCase().contains("video"))) {
                    if (videoMeetingUrl != null) {
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
                item.put("currency", srv != null ? srv.getCurrency() : (provider != null ? provider.getCurrency() : "DKK"));
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
                    ClientTherapistHandshake hs = hsOpt.get();
                    List<JournalEntry> cJournals = journalEntryRepository != null
                            ? journalEntryRepository.findByClientUuidOrderByTimestampDesc(cUuid)
                            : List.of();
                    cJournals = cJournals.stream()
                            .filter(j -> hs.isBranchAllowed(j.getGitBranch()))
                            .collect(Collectors.toList());
                    if (!cJournals.isEmpty()) {
                        JournalEntry latestEntry = cJournals.get(0);
                        if (!hs.hasRestrictionForBranch(latestEntry.getGitBranch(), "NO_MOOD_SCORES")) {
                            latestMood = latestEntry.getMoodRating();
                        }
                    }

                    if (!hs.hasCustomRestriction("NO_PCL5_SCORES")) {
                        List<PsychotraumatologyTest> cTests = psychotraumatologyTestRepository != null
                                ? psychotraumatologyTestRepository.findByClientUuid(cUuid)
                                : List.of();
                        latestPcl5Score = cTests.isEmpty() ? null : cTests.get(cTests.size() - 1).getScore();
                        latestPcl5Severity = cTests.isEmpty() ? null : cTests.get(cTests.size() - 1).getSeverity();
                    }
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
                cMap.put("allowedBranches", hsOpt.map(ClientTherapistHandshake::getAllowedBranches).orElse(null));
                cMap.put("customRestrictions", hsOpt.map(ClientTherapistHandshake::getCustomRestrictions).orElse(null));
                cMap.put("branchRestrictions", hsOpt.map(ClientTherapistHandshake::getBranchRestrictions).orElse(null));
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

        private boolean isTherapistService(Service s, ProviderProfile provider) {
            Optional<ProviderProfile> serviceProvider = s.findProvider();
            if (serviceProvider.isPresent()) {
                return Objects.equals(provider.getId(), serviceProvider.get().getId());
            }
            return provider.findOrganisation()
                    .map(Organisation::getId)
                    .filter(orgId -> s.findOrganisation().map(Organisation::getId).filter(sOrgId -> Objects.equals(orgId, sOrgId)).isPresent())
                    .isPresent();
        }

        @Transactional
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

        @Transactional
        public ProviderProfile onboardTherapist(Map<String, String> payload, Authentication auth) {
            String cvr = payload.getOrDefault("cvrNumber", "").trim();
            String orgName = payload.getOrDefault("organisationName", "").trim();
            ErrorCode.BAD_REQUEST.require(!cvr.isEmpty(), "CVR number is required");

            Organisation organisation = organisationRepository.findByCvrNumber(cvr)
                    .orElseGet(() -> {
                        ErrorCode.BAD_REQUEST.require(!orgName.isEmpty(), "Organisation name is required when creating a new CVR");
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
            if (payload.containsKey("iban") && payload.get("iban") != null) {
                organisation.setIban(payload.get("iban").trim());
            }
            if (payload.containsKey("currency") && payload.get("currency") != null && !payload.get("currency").isBlank()) {
                try {
                    organisation.setCurrency(payload.get("currency").trim());
                } catch (IllegalArgumentException e) {
                    throw ErrorCode.BAD_REQUEST.toException("Invalid ISO 4217 currency code: " + payload.get("currency"));
                }
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
            if (payload.containsKey("displayName")) {
                profile.setDisplayName(payload.getOrDefault("displayName", ""));
            } else if (profile.getDisplayName() == null) {
                profile.setDisplayName("");
            }
            profile.setOrganisation(organisation);
            if (payload.containsKey("biography") || payload.containsKey("bioDa")) {
                profile.setBiography(payload.getOrDefault("biography", payload.getOrDefault("bioDa", "")));
            } else if (profile.getBiography() == null) {
                profile.setBiography("");
            }
            if (payload.containsKey("workingHoursJson")) {
                profile.setWorkingHoursJson(payload.getOrDefault("workingHoursJson", ""));
            } else if (profile.getWorkingHoursJson() == null) {
                profile.setWorkingHoursJson("");
            }
            if (payload.containsKey("videoMeetingUrl") && payload.get("videoMeetingUrl") != null) {
                profile.setVideoMeetingUrl(payload.get("videoMeetingUrl").trim());
            } else if (profile.getVideoMeetingUrl() == null || profile.getVideoMeetingUrl().isBlank()) {
                profile.setVideoMeetingUrl(organisation.getVideoMeetingUrl());
            }
            if (payload.containsKey("iban") && payload.get("iban") != null) {
                profile.setIban(payload.get("iban").trim());
            } else if (profile.getIban() == null || profile.getIban().isBlank()) {
                profile.setIban(organisation.getIban());
            }
            if (payload.containsKey("currency") && payload.get("currency") != null && !payload.get("currency").isBlank()) {
                profile.setCurrency(organisation.getCurrency());
            }

            return providerProfileRepository.save(profile);
        }

        public List<Service> getAllServices() {
            return getServices(null, false);
        }

        public List<Service> getServices(UUID therapistUuid, boolean activeOnly) {
            List<Service> pool;
            if (therapistUuid == null) {
                pool = serviceRepository.findAll();
            } else {
                ProviderProfile provider = providerProfileRepository.findByUserUuid(therapistUuid).orElse(null);
                if (provider == null) {
                    return List.of();
                }
                pool = serviceRepository.findAll().stream()
                        .filter(s -> isTherapistService(s, provider))
                        .collect(Collectors.toList());
            }
            if (!activeOnly) {
                return pool;
            }
            return pool.stream()
                    .filter(s -> Boolean.TRUE.equals(s.getIsActive()))
                    .collect(Collectors.toList());
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

            String iban = Optional.ofNullable(provider)
                    .flatMap(ProviderProfile::findIban)
                    .or(() -> Optional.ofNullable(org).flatMap(Organisation::findIban))
                    .orElse("");
            if (iban.isBlank()) {
                missing.add("IBAN");
            }
            return missing;
        }

        @Transactional
        public Service createService(Map<String, Object> payload, Authentication auth) {
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
                    : null;

            List<String> missing = getMissingProfileFields(provider, organisation, clientProfile);
            ErrorCode.BAD_REQUEST.require(
                    missing.isEmpty(),
                    "Du kan ikke oprette en service/ydelse før du har udfyldt: " + String.join(", ", missing)
            );

            final UUID targetProviderId = provider != null ? provider.getId() : null;
            final UUID targetOrgId = organisation != null ? organisation.getId() : null;

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

            String rawCurrency = payload.get("currency") != null ? String.valueOf(payload.get("currency")).trim() : null;
            if (rawCurrency == null || rawCurrency.isBlank()) {
                rawCurrency = provider != null ? provider.getCurrency() : (organisation != null ? organisation.getCurrency() : "DKK");
            }
            try {
                service.setCurrency(rawCurrency);
            } catch (IllegalArgumentException e) {
                throw ErrorCode.BAD_REQUEST.toException("Invalid ISO 4217 currency code: " + rawCurrency);
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

        @Transactional
        public Service updateService(UUID id, Map<String, Object> payload, Authentication auth) {
            Service service = serviceRepository.findById(id)
                    .orElseThrow(ErrorCode.NOT_FOUND.supply("Service not found"));

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
                    : null;

            List<String> missing = getMissingProfileFields(provider, organisation, clientProfile);
            ErrorCode.BAD_REQUEST.require(
                    missing.isEmpty(),
                    "Du kan ikke opdatere en service/ydelse før du har udfyldt: " + String.join(", ", missing)
            );

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
            if (payload.containsKey("currency") && payload.get("currency") != null) {
                try {
                    service.setCurrency(String.valueOf(payload.get("currency")).trim());
                } catch (IllegalArgumentException e) {
                    throw ErrorCode.BAD_REQUEST.toException("Invalid ISO 4217 currency code: " + payload.get("currency"));
                }
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

        @Transactional
        public Map<String, Object> deleteService(UUID id) {
            Service service = serviceRepository.findById(id)
                    .orElseThrow(ErrorCode.NOT_FOUND.supply("Service not found"));

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
            return res;
        }

        @Transactional
        public Service toggleServiceActive(UUID id) {
            Service service = serviceRepository.findById(id)
                    .orElseThrow(ErrorCode.NOT_FOUND.supply("Service not found"));
            service.setIsActive(!service.getIsActive());
            return serviceRepository.save(service);
        }

        public List<ClientTherapistHandshake> getClientHandshakes(UUID clientUuid) {
            return handshakeRepository.findByClientUuid(clientUuid);
        }

        public List<ClientTherapistHandshake> getTherapistHandshakes(UUID therapistUuid) {
            return handshakeRepository.findByTherapistUuid(therapistUuid);
        }

        @Transactional
        public ResponseEntity<ClientTherapistHandshake> createOrUpdateHandshake(ClientTherapistHandshake req) {
            if (req.findClientName().isEmpty() && req.getClientUuid() != null) {
                clientProfileRepository.findByUserId(req.getClientUuid())
                        .ifPresent(cp -> req.setClientName(cp.getFullName()));
            }
            if (req.getTherapistUuid() != null) {
                providerProfileRepository.findByUserUuid(req.getTherapistUuid()).ifPresent(tp -> {
                    if (req.findTherapistName().isEmpty()) {
                        req.setTherapistName(tp.getDisplayName());
                    }
                    if (req.findOrganisationName().isEmpty() && tp.getOrganisation() != null) {
                        req.setOrganisationName(tp.getOrganisation().getName());
                    }
                });
            }

            Optional<ClientTherapistHandshake> existing = handshakeRepository.findByClientUuidAndTherapistUuid(
                    req.getClientUuid(), req.getTherapistUuid()
            );
            if (existing.isPresent()) {
                ClientTherapistHandshake current = existing.get();
                req.findClientName().ifPresent(current::setClientName);
                req.findTherapistName().ifPresent(current::setTherapistName);
                req.findOrganisationName().ifPresent(current::setOrganisationName);
                current.setStatus(Objects.requireNonNullElse(req.getStatus(), "ACTIVE"));
                req.findValidFrom().ifPresent(current::setValidFrom);
                req.findValidTo().ifPresent(current::setValidTo);
                req.findHistoryStartDate().ifPresent(current::setHistoryStartDate);
                req.findHistoryEndDate().ifPresent(current::setHistoryEndDate);
                req.findAllowedBranches().ifPresent(current::setAllowedBranches);
                req.findCustomRestrictions().ifPresent(current::setCustomRestrictions);
                req.findBranchRestrictions().ifPresent(current::setBranchRestrictions);
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

        @Transactional
        public ClientTherapistHandshake updateHandshakeStatus(UUID id, Map<String, String> body) {
            ClientTherapistHandshake handshake = handshakeRepository.findById(id)
                    .orElseThrow(ErrorCode.NOT_FOUND.supply("Handshake not found"));
            Optional.ofNullable(body.get("status"))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .map(String::toUpperCase)
                    .ifPresent(status -> {
                        handshake.setStatus(status);
                        if ("REVOKED".equals(status)) {
                            handshake.setRevokedAt(LocalDateTime.now());
                        } else if ("ACTIVE".equals(status)) {
                            handshake.setGrantedAt(LocalDateTime.now());
                            handshake.setRevokedAt(null);
                        }
                    });
            if (body.containsKey("validFrom")) {
                handshake.setValidFrom(Optional.ofNullable(body.get("validFrom"))
                        .map(String::trim).filter(s -> !s.isEmpty()).map(LocalDate::parse).orElse(null));
            }
            if (body.containsKey("validTo")) {
                handshake.setValidTo(Optional.ofNullable(body.get("validTo"))
                        .map(String::trim).filter(s -> !s.isEmpty()).map(LocalDate::parse).orElse(null));
            }
            if (body.containsKey("historyStartDate")) {
                handshake.setHistoryStartDate(Optional.ofNullable(body.get("historyStartDate"))
                        .map(String::trim).filter(s -> !s.isEmpty()).map(LocalDate::parse).orElse(null));
            }
            if (body.containsKey("historyEndDate")) {
                handshake.setHistoryEndDate(Optional.ofNullable(body.get("historyEndDate"))
                        .map(String::trim).filter(s -> !s.isEmpty()).map(LocalDate::parse).orElse(null));
            }
            if (body.containsKey("allowedBranches")) {
                handshake.setAllowedBranches(Optional.ofNullable(body.get("allowedBranches"))
                        .map(String::trim).filter(s -> !s.isEmpty()).orElse(null));
            }
            if (body.containsKey("customRestrictions")) {
                handshake.setCustomRestrictions(Optional.ofNullable(body.get("customRestrictions"))
                        .map(String::trim).filter(s -> !s.isEmpty()).orElse(null));
            }
            if (body.containsKey("branchRestrictions")) {
                handshake.setBranchRestrictions(Optional.ofNullable(body.get("branchRestrictions"))
                        .map(String::trim).filter(s -> !s.isEmpty()).orElse(null));
            }
            return handshakeRepository.save(handshake);
        }

        private Optional<Jwt> findJwt(Authentication auth) {
            return Optional.ofNullable(auth)
                    .map(Authentication::getPrincipal)
                    .filter(Jwt.class::isInstance)
                    .map(Jwt.class::cast);
        }

        private Jwt extractJwt(Authentication auth) {
            return findJwt(auth)
                    .orElseThrow(ErrorCode.UNAUTHORIZED.supply("Valid authenticated user is required"));
        }

        private UUID extractUserUuid(Jwt jwt) {
            return Optional.ofNullable(jwt.getSubject())
                    .map(String::trim)
                    .flatMap(sub -> {
                        try {
                            return Optional.of(UUID.fromString(sub));
                        } catch (IllegalArgumentException ignored) {
                            return Optional.empty();
                        }
                    })
                    .orElseThrow(ErrorCode.UNAUTHORIZED.supply("Valid authenticated user UUID is required"));
        }

        private String extractClaim(Jwt jwt, String claim, String fallback) {
            return Optional.ofNullable(jwt.getClaimAsString(claim))
                    .filter(val -> !val.isBlank())
                    .orElse(fallback);
        }

        private List<String> extractRoles(Jwt jwt) {
            return Optional.ofNullable(jwt.getClaimAsMap("realm_access"))
                    .map(m -> m.get("roles"))
                    .filter(List.class::isInstance)
                    .map(List.class::cast)
                    .map(list -> ((List<?>) list).stream().map(String::valueOf).collect(Collectors.toList()))
                    .orElseGet(List::of);
        }
    }

    // =========================================================================
    // 5. REST CONTROLLERS (Package-Private)
    // =========================================================================

    @RestController
    @RequestMapping("/api/v1")
    @CrossOrigin(origins = "*")
    static class ClinicPlatformController extends BaseController<ClientProfile, ClientProfileDto> {

        private final ClinicPlatformService clinicPlatformService;

        ClinicPlatformController(ClinicPlatformService clinicPlatformService) {
            super(clinicPlatformService);
            this.clinicPlatformService = clinicPlatformService;
        }

        @Override
        @GetMapping("/clinic/clients")
        public ResponseEntity<List<ClientProfileDto>> getAll() {
            return super.getAll();
        }

        @Override
        @GetMapping("/clinic/clients/paged")
        public ResponseEntity<Page<ClientProfileDto>> getAllPaged(Pageable pageable) {
            return super.getAllPaged(pageable);
        }

        @Override
        @GetMapping("/clinic/clients/{id}")
        public ResponseEntity<ClientProfileDto> getById(@PathVariable UUID id) {
            return super.getById(id);
        }

        @Override
        @PostMapping("/clinic/clients")
        public ResponseEntity<ClientProfileDto> create(@Valid @RequestBody ClientProfileDto dto) {
            return super.create(dto);
        }

        @Override
        @PutMapping("/clinic/clients/{id}")
        public ResponseEntity<ClientProfileDto> update(@PathVariable UUID id, @Valid @RequestBody ClientProfileDto dto) {
            return super.update(id, dto);
        }

        @Override
        @DeleteMapping("/clinic/clients/{id}")
        public ResponseEntity<Void> delete(@PathVariable UUID id) {
            return super.delete(id);
        }

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

        public ResponseEntity<List<Service>> getAllServices() {
            return ResponseEntity.ok(clinicPlatformService.getAllServices());
        }

        @GetMapping("/clinic/services")
        public ResponseEntity<List<Service>> getServices(
                @RequestParam(required = false) UUID therapistUuid,
                @RequestParam(required = false, defaultValue = "false") boolean activeOnly
        ) {
            return ResponseEntity.ok(clinicPlatformService.getServices(therapistUuid, activeOnly));
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
}
