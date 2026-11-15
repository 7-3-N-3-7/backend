package com.platform.features.psychotraumatology;

import com.platform.core.BaseDomainCore.BaseRepository;
import com.platform.core.BaseDomainCore.BaseService;
import com.platform.core.BaseDomainCore.ErrorCode;
import com.platform.core.BaseDomainCore.HasId;
import com.platform.core.BaseDomainCore.Identifiable;
import com.platform.core.BaseWebCore.BaseController;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.validation.Valid;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Repository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * ============================================================================
 * ARKITEKTONISK VISITKORT: PsychotraumatologyFeature (Vertical Feature Slice)
 * ============================================================================
 * @Spejler: frontend/src/features/psychotraumatology/PsychotraumatologyFeature.tsx
 *
 * FORMÅL (Til Censor, Vejleder & Nykommende Udviklere):
 * Implementerer kliniske spørgeskemaer og traumescreening (DSM-5 PCL-5 & ITQ)
 * som en isoleret vertikal slice:
 *
 * ARKITEKTONISK OVERBLIK (5 TRIN):
 *   1. [Record/DTO: PsychotraumatologyTestDto]
 *      Uforanderlig Java 17 `record` med `score`, `severity` og `answersJson`.
 *   2. [Entity: PsychotraumatologyTest]
 *      JPA-entitet i tabellen `psychotraumatology_tests`.
 *   3. [Interface: PsychotraumatologyPort] & [Repository: PsychotraumatologyTestRepository]
 *      Smalt offentligt port-interface til tværgående GDPR-eksport, mens selve
 *      JPA-repositoriet holdes package-private.
 *   4. [Service: PsychotraumatologyService]
 *      Håndhæver bl.a. Zero-Trust ejerskabstjek i #getTestsByClient(clientUuid, authentication)
 *      mod kaldets JWT `sub`-claim, så en klient kun kan tilgå egne testresultater.
 *   5. [Controller: PsychotraumatologyController]
 *      REST-controller under `/api/v1/psychotraumatology/tests`.
 * ============================================================================
 */
public final class PsychotraumatologyFeature {

    /** Mindste mulige DSM-5 PCL-5 totalscore (20 spørgsmål á 0 point). */
    public static final int MIN_PCL5_SCORE = 0;

    /** Højeste mulige DSM-5 PCL-5 totalscore (20 spørgsmål á 4 point). */
    public static final int MAX_PCL5_SCORE = 80;

    /** Klinisk cut-off score (>= 33 indikerer sandsynlig PTSD jf. DSM-5). */
    public static final int CLINICAL_PTSD_CUTOFF_SCORE = 33;

    private static final UUID DEFAULT_FALLBACK_UUID =
            UUID.fromString("00000000-0000-0000-0000-000000000000");

    private PsychotraumatologyFeature() {}

    // =========================================================================
    // 1. DTO CONTRACTS (Public Java Records implementing HasId)
    // =========================================================================

    /**
     * [Record/DTO: PsychotraumatologyTestDto]
     * Datakontrakt for en gennemført psykotraumatologisk screening (f.eks. PCL-5).
     */
    public record PsychotraumatologyTestDto(
            UUID id,
            UUID clientUuid,
            UUID therapistUuid,
            String testType,
            Integer score,
            String severity,
            String answersJson,
            LocalDateTime timestamp
    ) implements HasId {

        public PsychotraumatologyTestDto(
                UUID id,
                UUID clientUuid,
                UUID therapistUuid,
                String testType,
                Integer score,
                String severity,
                String answersJson) {
            this(id, clientUuid, therapistUuid, testType, score, severity, answersJson, null);
        }

        public UUID getId() { return id; }
        public UUID getClientUuid() { return clientUuid; }
        public UUID getTherapistUuid() { return therapistUuid; }
        public String getTestType() { return testType; }
        public Integer getScore() { return score; }
        public String getSeverity() { return severity; }
        public String getAnswersJson() { return answersJson; }
        public LocalDateTime getTimestamp() { return timestamp; }
    }

    // =========================================================================
    // 2. JPA / MONGO ENTITIES (Implements Identifiable)
    // =========================================================================

    /**
     * [Entity: PsychotraumatologyTest]
     * JPA-entitet for lagrede traumescreeninger i `psychotraumatology_tests`.
     */
    @Entity(name = "PsychotraumatologyTest")
    @Table(name = "psychotraumatology_tests")
    public static class PsychotraumatologyTest implements Identifiable {

        @Id
        @GeneratedValue(strategy = GenerationType.UUID)
        private UUID id;

        @Column(name = "client_uuid", nullable = false)
        private UUID clientUuid;

        @Column(name = "therapist_uuid", nullable = true)
        private UUID therapistUuid;

        @Column(name = "test_type", nullable = false)
        private String testType;

        @Column(nullable = false)
        private Integer score;

        @Column(nullable = false)
        private String severity;

        @Column(name = "answers_json", columnDefinition = "TEXT", nullable = false)
        private String answersJson;

        @Column(nullable = false)
        private LocalDateTime timestamp;

        public PsychotraumatologyTest() {}

        @PrePersist
        protected void onCreate() {
            if (timestamp == null) {
                timestamp = LocalDateTime.now();
            }
        }

        @Override public UUID getId() { return id; }
        @Override public void setId(UUID id) { this.id = id; }

        public UUID getClientUuid() { return clientUuid; }
        public void setClientUuid(UUID clientUuid) { this.clientUuid = clientUuid; }

        public UUID getTherapistUuid() { return therapistUuid; }
        public void setTherapistUuid(UUID therapistUuid) { this.therapistUuid = therapistUuid; }

        public String getTestType() { return testType; }
        public void setTestType(String testType) { this.testType = testType; }

        public Integer getScore() { return score; }
        public void setScore(Integer score) { this.score = score; }

        public String getSeverity() { return severity; }
        public void setSeverity(String severity) { this.severity = severity; }

        public String getAnswersJson() { return answersJson; }
        public void setAnswersJson(String answersJson) { this.answersJson = answersJson; }

        public LocalDateTime getTimestamp() { return timestamp; }
        public void setTimestamp(LocalDateTime timestamp) { this.timestamp = timestamp; }
    }

    // =========================================================================
    // 3. REPOSITORIES & PORTS (Package-Private Repository + Public Port)
    // =========================================================================

    /**
     * [Interface: PsychotraumatologyPort]
     * Offentlig port, der giver `ClinicPlatformFeature` mulighed for at indsamle
     * klientens testresultater ved GDPR Art. 15/20 dataeksport uden at eksponere JPA-repositoriet.
     */
    public interface PsychotraumatologyPort {
        List<PsychotraumatologyTest> findByClientUuid(UUID clientUuid);
        void deleteAll();
    }

    /**
     * [Repository: PsychotraumatologyTestRepository]
     * Package-private Spring Data JPA repository for [Entity: PsychotraumatologyTest].
     */
    @Repository
    interface PsychotraumatologyTestRepository extends BaseRepository<PsychotraumatologyTest>, PsychotraumatologyPort {
        @Override
        List<PsychotraumatologyTest> findByClientUuid(UUID clientUuid);
    }

    // =========================================================================
    // 4. DOMAIN SERVICES (Package-Private)
    // =========================================================================

    /**
     * [Service: PsychotraumatologyService]
     * Domæneservice for indsendelse og Zero-Trust udlevering af traumescreeninger.
     */
    @Service
    static class PsychotraumatologyService extends BaseService<PsychotraumatologyTest, PsychotraumatologyTestDto> {

        private final PsychotraumatologyTestRepository testRepository;

        PsychotraumatologyService(PsychotraumatologyTestRepository testRepository) {
            super(testRepository);
            this.testRepository = testRepository;
        }

        @Override
        protected PsychotraumatologyTestDto toDto(PsychotraumatologyTest testEntity) {
            if (testEntity == null) {
                return null;
            }
            return new PsychotraumatologyTestDto(
                    testEntity.getId(),
                    testEntity.getClientUuid(),
                    testEntity.getTherapistUuid(),
                    testEntity.getTestType(),
                    testEntity.getScore(),
                    testEntity.getSeverity(),
                    testEntity.getAnswersJson(),
                    testEntity.getTimestamp()
            );
        }

        @Override
        public PsychotraumatologyTest toEntity(PsychotraumatologyTestDto testDto) {
            if (testDto == null) {
                return null;
            }
            PsychotraumatologyTest testEntity = new PsychotraumatologyTest();
            testEntity.setId(testDto.id());
            testEntity.setClientUuid(testDto.clientUuid());
            testEntity.setTherapistUuid(
                    Optional.ofNullable(testDto.therapistUuid())
                            .or(() -> Optional.ofNullable(testDto.clientUuid()))
                            .orElse(DEFAULT_FALLBACK_UUID)
            );
            testEntity.setTestType(Optional.ofNullable(testDto.testType()).orElse("PCL-5"));
            testEntity.setScore(Optional.ofNullable(testDto.score()).orElse(MIN_PCL5_SCORE));
            testEntity.setSeverity(Optional.ofNullable(testDto.severity()).orElse("Unknown"));
            testEntity.setAnswersJson(Optional.ofNullable(testDto.answersJson()).orElse("[]"));
            testEntity.setTimestamp(Optional.ofNullable(testDto.timestamp()).orElseGet(LocalDateTime::now));
            return testEntity;
        }

        /**
         * #submitTest(testDto) — Persisterer en ny traumescreening i `psychotraumatology_tests`.
         */
        @Transactional
        public PsychotraumatologyTest submitTest(PsychotraumatologyTestDto testDto) {
            PsychotraumatologyTest testEntity = toEntity(testDto);
            testEntity.setId(null);
            return testRepository.save(testEntity);
        }

        /**
         * #getTestsByClient(clientUuid, authentication) — Håndhæver Zero-Trust ejerskabskontrol,
         * så en indlogget klient kun kan hente sine egne testresultater.
         */
        public List<PsychotraumatologyTest> getTestsByClient(UUID clientUuid, Authentication authentication) {
            // Trin 1: Verificer at kaldet indeholder et gyldigt JWT principal
            if (authentication == null || !(authentication.getPrincipal() instanceof Jwt jwt)) {
                throw ErrorCode.UNAUTHORIZED.toException("Unauthorized");
            }

            // Trin 2: Sammenhold JWT `sub`-claim (`callerUuid`) med den efterspurgte `clientUuid`
            UUID callerUuid = UUID.fromString(jwt.getSubject());
            ErrorCode.FORBIDDEN.require(
                    callerUuid.equals(clientUuid),
                    "Forbidden access to other client's records"
            );

            // Trin 3: Returner klientens testresultater fra `testRepository`
            return testRepository.findByClientUuid(clientUuid);
        }
    }

    // =========================================================================
    // 5. REST CONTROLLERS (Package-Private)
    // =========================================================================

    /**
     * [Controller: PsychotraumatologyController]
     * REST-controller for `/api/v1/psychotraumatology/tests`.
     */
    @RestController
    @RequestMapping("/api/v1/psychotraumatology/tests")
    static class PsychotraumatologyController extends BaseController<PsychotraumatologyTest, PsychotraumatologyTestDto> {

        private final PsychotraumatologyService psychotraumatologyService;

        PsychotraumatologyController(PsychotraumatologyService psychotraumatologyService) {
            super(psychotraumatologyService);
            this.psychotraumatologyService = psychotraumatologyService;
        }

        @Override
        @PostMapping
        public ResponseEntity<PsychotraumatologyTestDto> create(@Valid @RequestBody PsychotraumatologyTestDto testDto) {
            PsychotraumatologyTest savedTest = psychotraumatologyService.submitTest(testDto);
            return ResponseEntity.ok(psychotraumatologyService.toDto(savedTest));
        }

        @GetMapping("/client/{clientUuid}")
        public ResponseEntity<List<PsychotraumatologyTestDto>> getTestsByClient(
                @PathVariable UUID clientUuid,
                Authentication authentication) {
            return ResponseEntity.ok(
                    psychotraumatologyService.getTestsByClient(clientUuid, authentication)
                            .stream()
                            .map(psychotraumatologyService::toDto)
                            .toList()
            );
        }
    }
}
