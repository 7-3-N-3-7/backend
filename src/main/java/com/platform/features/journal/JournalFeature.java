package com.platform.features.journal;

import com.platform.config.PlatformCacheService;
import com.platform.core.BaseDomainCore.BaseRepository;
import com.platform.core.BaseDomainCore.BaseService;
import com.platform.core.BaseDomainCore.ErrorCode;
import com.platform.core.BaseDomainCore.HasId;
import com.platform.core.BaseDomainCore.Identifiable;
import com.platform.core.BaseWebCore.BaseController;
import com.platform.features.appointment.AppointmentFeature.AppointmentPort;
import com.platform.features.clinic.ClinicPlatformFeature.ClientTherapistHandshake;
import com.platform.features.clinic.ClinicPlatformFeature.HandshakePort;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectLoader;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.eclipse.jgit.treewalk.filter.PathFilter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiFunction;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * ============================================================================
 * ARKITEKTONISK VISITKORT: JournalFeature (Vertical Feature Slice — Eclipse JGit)
 * ============================================================================
 * @Spejler: frontend/src/features/journal/JournalFeature.tsx
 *
 * FORMÅL (Til Censor, Vejleder & Nykommende Udviklere):
 * Denne fil implementerer platformens medicinske og persondataretlige (GDPR)
 * kernefunktionalitet i én selvbærende vertikal slice efter 5-trins strukturen:
 *
 * ARKITEKTONISK OVERBLIK (5 TRIN):
 *   1. [Record/DTO: JournalEntryDto], [Record/DTO: TherapistClinicalNoteDto] & [Record/DTO: TherapistNoteDto]
 *      Java 17 `record` kontrakter der implementerer [Interface: HasId].
 *   2. [Entity: JournalEntry], [Entity: TherapistClinicalNote] & [Entity: TherapistNote]
 *      JPA-entiteter i `journal_entries` og `therapist_clinical_notes`.
 *   3. [Service: JournalGitService], [Interface: JournalPort] & Package-Private Repositories
 *      - [Service: JournalGitService]: Persisterer hver journalændring som et uforanderligt
 *        Eclipse JGit-commit med kryptografisk SHA-1 hash og emnegren (`[Branch: main|trauma|sleep]`).
 *      - #filterInternalNotes(rawMarkdown, isTherapist) & #isPrivateClientBookingCommit:
 *        Håndhæver tovejs Privacy-by-Design filtrering mellem klient og terapeut.
 *   4. [Service: JournalService]
 *      Arver fra [AbstractClass: BaseService<JournalEntry, JournalEntryDto>] og håndhæver
 *      aktivt `ClientTherapistHandshake`-samtykke (datogrænser, tilladte grene og
 *      restriktioner som `NO_MOOD_SCORES`).
 *   5. [Controller: JournalController]
 *      REST-controller under `/api/v1/journals` og `/api/v1/journal`.
 * ============================================================================
 */
public final class JournalFeature {

    public static final String PRIVATE_BOOKING_COMMIT_AUTHOR = "Klinik Booking";

    private JournalFeature() {}

    private static final String INTERNAL_NOTES_MARKER = "<!-- INTERNAL_MEDICAL_NOTES -->";
    private static final Pattern INTERNAL_NOTES_PATTERN = Pattern.compile(
            INTERNAL_NOTES_MARKER + "[\\s\\S]*?(?=(\\r?\\n## |\\r?\\n" + INTERNAL_NOTES_MARKER + "|$))"
    );

    /**
     * Fjerner fortrolige kliniske behandlernotater ({@code <!-- INTERNAL_MEDICAL_NOTES -->}),
     * hvis kalderen ikke har behandlerrollen ({@code isTherapist == false}).
     */
    public static String filterInternalNotes(String rawMarkdown, boolean isTherapist) {
        if (rawMarkdown == null) {
            return "";
        }
        if (isTherapist) {
            return rawMarkdown;
        }
        return INTERNAL_NOTES_PATTERN.matcher(rawMarkdown)
                .replaceAll("")
                .replaceAll("\\n{3,}", "\n\n")
                .trim();
    }

    // =========================================================================
    // 1. DTO CONTRACTS (Public Java Records implementing HasId)
    // =========================================================================

    public record JournalEntryDto(
            UUID id,
            String title,
            String content,
            Integer moodRating,
            Boolean sharedWithTherapists,
            List<UUID> sharedTherapistUuids,
            UUID clientUuid,
            UUID appointmentId,
            String gitBranch,
            LocalDateTime timestamp,
            String gitCommitHash
    ) implements HasId {

        public JournalEntryDto(
                UUID id,
                String title,
                String content,
                Integer moodRating,
                Boolean sharedWithTherapists,
                List<UUID> sharedTherapistUuids,
                UUID clientUuid,
                UUID appointmentId,
                String gitBranch
        ) {
            this(id, title, content, moodRating, sharedWithTherapists, sharedTherapistUuids, clientUuid, appointmentId, gitBranch, null, null);
        }

        public UUID getId() { return id; }
        public String getTitle() { return title; }
        public String getContent() { return content; }
        public Integer getMoodRating() { return moodRating; }
        public Boolean getSharedWithTherapists() { return sharedWithTherapists != null ? sharedWithTherapists : true; }
        public List<UUID> getSharedTherapistUuids() { return sharedTherapistUuids; }
        public UUID getClientUuid() { return clientUuid; }
        public UUID getAppointmentId() { return appointmentId; }
        public String getGitBranch() { return (gitBranch != null && !gitBranch.isBlank()) ? gitBranch.trim() : "main"; }
        public LocalDateTime getTimestamp() { return timestamp; }
        public String getGitCommitHash() { return gitCommitHash; }
    }

    public record TherapistClinicalNoteDto(
            UUID id,
            UUID clientUuid,
            UUID authorTherapistUuid,
            String authorTherapistName,
            String organisationName,
            UUID journalEntryId,
            UUID appointmentId,
            String confidentialMarkdown,
            String gitCommitHash,
            LocalDateTime createdAt
    ) implements HasId {
        public static TherapistClinicalNoteDto fromEntity(TherapistClinicalNote entity) {
            return new TherapistClinicalNoteDto(
                    entity.getId(),
                    entity.getClientUuid(),
                    entity.getAuthorTherapistUuid(),
                    entity.getAuthorTherapistName(),
                    entity.getOrganisationName(),
                    entity.getJournalEntryId(),
                    entity.getAppointmentId(),
                    entity.getConfidentialMarkdown(),
                    entity.getGitCommitHash(),
                    entity.getCreatedAt()
            );
        }
    }

    public record TherapistNoteDto(
            UUID id,
            UUID clientUuid,
            UUID therapistUuid,
            String note,
            LocalDateTime timestamp
    ) implements HasId {
        public static TherapistNoteDto fromEntity(TherapistNote entity) {
            return new TherapistNoteDto(
                    entity.getId(),
                    entity.getClientUuid(),
                    entity.getTherapistUuid(),
                    entity.getNote(),
                    entity.getTimestamp()
            );
        }
    }

    // =========================================================================
    // 2. JPA / MONGO ENTITIES (Implements Identifiable)
    // =========================================================================

    @Entity(name = "JournalEntry")
    @Table(name = "journal_entries")
    public static class JournalEntry implements Identifiable {

        @Id
        @GeneratedValue(strategy = GenerationType.UUID)
        private UUID id;

        @Column(nullable = false, length = 255)
        private String title;

        @Column(nullable = false, columnDefinition = "TEXT")
        private String content;

        @Column(nullable = true)
        private Integer moodRating;

        @Column(nullable = false)
        private LocalDateTime timestamp;

        @Column(name = "client_uuid", nullable = false)
        private UUID clientUuid;

        @Column(name = "is_shared_with_therapists")
        private Boolean sharedWithTherapists = true;

        @Column(name = "shared_therapist_uuids", columnDefinition = "TEXT")
        private String sharedTherapistUuidsCsv;

        @Column(name = "git_commit_hash")
        private String gitCommitHash;

        @Column(name = "git_branch", length = 100)
        private String gitBranch = "main";

        @Column(name = "appointment_id")
        private UUID appointmentId;

        public JournalEntry() {}

        public JournalEntry(
                String title,
                String content,
                Integer moodRating,
                LocalDateTime timestamp,
                UUID clientUuid
        ) {
            this.title = title;
            this.content = content;
            this.moodRating = moodRating;
            this.timestamp = timestamp;
            this.clientUuid = clientUuid;
            this.sharedWithTherapists = true;
        }

        @PrePersist
        protected void onCreate() {
            if (timestamp == null) {
                timestamp = LocalDateTime.now();
            }
            if (sharedWithTherapists == null) {
                sharedWithTherapists = true;
            }
        }

        @Override public UUID getId() { return id; }
        @Override public void setId(UUID id) { this.id = id; }

        public String getTitle() { return title; }
        public void setTitle(String title) { this.title = title; }

        public String getContent() { return content; }
        public void setContent(String content) { this.content = content; }

        public Integer getMoodRating() { return moodRating; }
        public void setMoodRating(Integer moodRating) { this.moodRating = moodRating; }

        public LocalDateTime getTimestamp() { return timestamp; }
        public void setTimestamp(LocalDateTime timestamp) { this.timestamp = timestamp; }

        public UUID getClientUuid() { return clientUuid; }
        public void setClientUuid(UUID clientUuid) { this.clientUuid = clientUuid; }

        public Boolean getSharedWithTherapists() { return sharedWithTherapists != null ? sharedWithTherapists : true; }
        public void setSharedWithTherapists(Boolean sharedWithTherapists) { this.sharedWithTherapists = sharedWithTherapists; }

        public List<UUID> getSharedTherapistUuids() {
            return Optional.ofNullable(sharedTherapistUuidsCsv)
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .map(raw -> {
                        List<UUID> list = new ArrayList<>();
                        for (String part : raw.split(",")) {
                            String trimmed = part.trim();
                            if (!trimmed.isEmpty()) {
                                try {
                                    list.add(UUID.fromString(trimmed));
                                } catch (IllegalArgumentException ignored) {
                                }
                            }
                        }
                        return list;
                    })
                    .orElseGet(List::of);
        }

        public void setSharedTherapistUuids(List<UUID> uuids) {
            this.sharedTherapistUuidsCsv = Optional.ofNullable(uuids)
                    .filter(list -> !list.isEmpty())
                    .map(list -> list.stream()
                            .filter(Objects::nonNull)
                            .distinct()
                            .map(UUID::toString)
                            .collect(Collectors.joining(",")))
                    .orElse(null);
        }

        public boolean isVisibleToTherapist(UUID therapistUuid) {
            if (!Boolean.TRUE.equals(getSharedWithTherapists())) {
                return false;
            }
            List<UUID> allowed = getSharedTherapistUuids();
            return allowed.isEmpty() || Optional.ofNullable(therapistUuid).map(allowed::contains).orElse(false);
        }

        public String getGitCommitHash() { return gitCommitHash; }
        public void setGitCommitHash(String gitCommitHash) { this.gitCommitHash = gitCommitHash; }

        public String getGitBranch() {
            return Optional.ofNullable(gitBranch)
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .orElse("main");
        }

        public void setGitBranch(String gitBranch) {
            this.gitBranch = Optional.ofNullable(gitBranch)
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .orElse("main");
        }

        public UUID getAppointmentId() { return appointmentId; }
        public void setAppointmentId(UUID appointmentId) { this.appointmentId = appointmentId; }
    }

    @Entity(name = "TherapistClinicalNote")
    @Table(name = "therapist_clinical_notes")
    public static class TherapistClinicalNote implements Identifiable {

        @Id
        @GeneratedValue(strategy = GenerationType.UUID)
        private UUID id;

        @Column(name = "client_uuid", nullable = false)
        private UUID clientUuid;

        @Column(name = "author_therapist_uuid", nullable = false)
        private UUID authorTherapistUuid;

        @Column(name = "author_therapist_name")
        private String authorTherapistName;

        @Column(name = "organisation_name")
        private String organisationName;

        @Column(name = "journal_entry_id")
        private UUID journalEntryId;

        @Column(name = "appointment_id")
        private UUID appointmentId;

        @Column(name = "confidential_markdown", columnDefinition = "TEXT", nullable = false)
        private String confidentialMarkdown;

        @Column(name = "git_commit_hash")
        private String gitCommitHash;

        @Column(name = "created_at", nullable = false)
        private LocalDateTime createdAt;

        public TherapistClinicalNote() {}

        public TherapistClinicalNote(
                UUID clientUuid,
                UUID authorTherapistUuid,
                String authorTherapistName,
                String organisationName,
                UUID journalEntryId,
                String confidentialMarkdown,
                String gitCommitHash
        ) {
            this(clientUuid, authorTherapistUuid, authorTherapistName, organisationName, journalEntryId, null, confidentialMarkdown, gitCommitHash);
        }

        public TherapistClinicalNote(
                UUID clientUuid,
                UUID authorTherapistUuid,
                String authorTherapistName,
                String organisationName,
                UUID journalEntryId,
                UUID appointmentId,
                String confidentialMarkdown,
                String gitCommitHash
        ) {
            this.clientUuid = clientUuid;
            this.authorTherapistUuid = authorTherapistUuid;
            this.authorTherapistName = authorTherapistName;
            this.organisationName = organisationName;
            this.journalEntryId = journalEntryId;
            this.appointmentId = appointmentId;
            this.confidentialMarkdown = confidentialMarkdown;
            this.gitCommitHash = gitCommitHash;
            this.createdAt = LocalDateTime.now();
        }

        @PrePersist
        protected void onCreate() {
            if (createdAt == null) {
                createdAt = LocalDateTime.now();
            }
        }

        @Override public UUID getId() { return id; }
        @Override public void setId(UUID id) { this.id = id; }

        public UUID getClientUuid() { return clientUuid; }
        public void setClientUuid(UUID clientUuid) { this.clientUuid = clientUuid; }

        public UUID getAuthorTherapistUuid() { return authorTherapistUuid; }
        public void setAuthorTherapistUuid(UUID authorTherapistUuid) { this.authorTherapistUuid = authorTherapistUuid; }

        public String getAuthorTherapistName() { return authorTherapistName; }
        public void setAuthorTherapistName(String authorTherapistName) { this.authorTherapistName = authorTherapistName; }

        public String getOrganisationName() { return organisationName; }
        public void setOrganisationName(String organisationName) { this.organisationName = organisationName; }

        public UUID getJournalEntryId() { return journalEntryId; }
        public void setJournalEntryId(UUID journalEntryId) { this.journalEntryId = journalEntryId; }

        public UUID getAppointmentId() { return appointmentId; }
        public void setAppointmentId(UUID appointmentId) { this.appointmentId = appointmentId; }

        public String getConfidentialMarkdown() { return confidentialMarkdown; }
        public void setConfidentialMarkdown(String confidentialMarkdown) { this.confidentialMarkdown = confidentialMarkdown; }

        public String getGitCommitHash() { return gitCommitHash; }
        public void setGitCommitHash(String gitCommitHash) { this.gitCommitHash = gitCommitHash; }

        public LocalDateTime getCreatedAt() { return createdAt; }
        public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    }

    @Entity(name = "TherapistNote")
    @Table(name = "therapist_notes")
    public static class TherapistNote implements Identifiable {

        @Id
        @GeneratedValue(strategy = GenerationType.UUID)
        private UUID id;

        @Column(name = "client_uuid", nullable = false)
        private UUID clientUuid;

        @Column(name = "therapist_uuid", nullable = false)
        private UUID therapistUuid;

        @Column(nullable = false, columnDefinition = "TEXT")
        private String note;

        @Column(nullable = false)
        private LocalDateTime timestamp;

        protected TherapistNote() {}

        public TherapistNote(UUID clientUuid, UUID therapistUuid, String note) {
            this.clientUuid = clientUuid;
            this.therapistUuid = therapistUuid;
            this.note = note;
        }

        @PrePersist
        protected void onCreate() {
            if (timestamp == null) {
                timestamp = LocalDateTime.now();
            }
        }

        @Override public UUID getId() { return id; }
        @Override public void setId(UUID id) { this.id = id; }

        public UUID getClientUuid() { return clientUuid; }
        public UUID getTherapistUuid() { return therapistUuid; }
        public String getNote() { return note; }
        public LocalDateTime getTimestamp() { return timestamp; }
    }

    // =========================================================================
    // 3. JGIT INFRASTRUCTURE SERVICE (Immutable SHA-1 Audit Trail)
    // =========================================================================

    @Service
    public static class JournalGitService {

        private static final Logger log = LoggerFactory.getLogger(JournalGitService.class);

        @Value("${journal.git.repo-path:./journal-repo}")
        private String repoPath;

        private Git git;
        private final Map<String, List<Map<String, String>>> historyCache = new ConcurrentHashMap<>();

        @PostConstruct
        public void init() {
            try {
                File repoDir = new File(repoPath);
                File gitDir = new File(repoDir, ".git");
                if (!gitDir.exists()) {
                    Files.createDirectories(Path.of(repoPath));
                    git = Git.init().setDirectory(repoDir).call();
                    log.info("Initialized new Git repository at {}", repoDir.getAbsolutePath());
                } else {
                    Repository repository = new FileRepositoryBuilder()
                            .setGitDir(gitDir)
                            .readEnvironment()
                            .build();
                    git = new Git(repository);
                    log.info("Loaded existing Git repository at {}", repoDir.getAbsolutePath());
                }
            } catch (IOException | GitAPIException e) {
                log.error("Failed to initialize Git repository for journals", e);
                throw new RuntimeException("Git repository initialization failed", e);
            }
        }

        public void commitJournalEntry(String patientId, String entryContent, String commitMessage) {
            commitJournalEntryWithHash(patientId, entryContent, commitMessage, "Platform Audit");
        }

        public String commitJournalEntryWithHash(String patientId, String entryContent, String commitMessage, String authorName) {
            return commitJournalEntryWithHash(patientId, entryContent, commitMessage, authorName, "main");
        }

        public String commitJournalEntryWithHash(String patientId, String entryContent, String commitMessage, String authorName, String branchName) {
            try {
                String effectiveBranch = (branchName != null && !branchName.isBlank()) ? branchName.trim() : "main";
                String taggedMessage = commitMessage != null && commitMessage.startsWith("[Branch:")
                        ? commitMessage
                        : "[Branch: " + effectiveBranch + "] " + Objects.requireNonNullElse(commitMessage, "Journal commit");
                File entryFile = new File(repoPath, patientId + ".md");
                Files.writeString(entryFile.toPath(), entryContent + "\n\n",
                        StandardOpenOption.CREATE,
                        StandardOpenOption.APPEND);

                git.add().addFilepattern(patientId + ".md").call();
                RevCommit commit = git.commit()
                        .setSign(false)
                        .setAuthor(Objects.requireNonNullElse(authorName, "Platform Audit"), "audit@platform.local")
                        .setMessage(taggedMessage)
                        .call();

                log.info("Committed journal entry for patient {} on branch {}", patientId, effectiveBranch);
                historyCache.keySet().removeIf(key -> key.startsWith(patientId + ":"));
                return commit.getName().substring(0, 8);
            } catch (IOException | GitAPIException e) {
                log.error("Failed to commit journal entry for patient " + patientId, e);
                throw new RuntimeException("Commit failed", e);
            }
        }

        public String readJournalEntry(String patientId) {
            try {
                File entryFile = new File(repoPath, patientId + ".md");
                if (!entryFile.exists()) return "";
                return Files.readString(entryFile.toPath());
            } catch (IOException e) {
                throw new RuntimeException("Failed to read journal", e);
            }
        }

        public static String extractBranchFromMessage(String commitMessage) {
            if (commitMessage != null && commitMessage.startsWith("[Branch:")) {
                int endIdx = commitMessage.indexOf(']');
                if (endIdx > 8) {
                    String extracted = commitMessage.substring(8, endIdx).trim();
                    if (!extracted.isEmpty()) {
                        return extracted;
                    }
                }
            }
            return "main";
        }

        public List<Map<String, String>> getCommitHistory(
                String patientId,
                boolean isTherapist,
                BiFunction<String, Boolean, String> contentFilter
        ) {
            String cacheKey = patientId + ":" + isTherapist;
            List<Map<String, String>> cached = historyCache.get(cacheKey);
            if (cached != null) {
                return cached;
            }

            List<Map<String, String>> history = new ArrayList<>();
            String fileName = patientId + ".md";
            File entryFile = new File(repoPath, fileName);
            if (!entryFile.exists()) {
                return history;
            }
            try {
                Iterable<RevCommit> commits = git.log().addPath(fileName).call();
                Repository repository = git.getRepository();
                for (RevCommit commit : commits) {
                    String fullMsg = commit.getFullMessage();
                    String branch = extractBranchFromMessage(fullMsg);
                    String strippedMsg = fullMsg != null && fullMsg.startsWith("[Branch:") && fullMsg.indexOf(']') > 0
                            ? fullMsg.substring(fullMsg.indexOf(']') + 1).trim()
                            : fullMsg;
                    // Trin 2: GDPR Privatlivsfilter — Terapeuter må aldrig se klientens rå booking-commits
                    if (isTherapist) {
                        String authorName = commit.getAuthorIdent() != null ? commit.getAuthorIdent().getName() : "";
                        if (isPrivateClientBookingCommit(authorName, strippedMsg)) {
                            continue;
                        }
                    }

                    // Trin 3: Læs fil-snapshot på commit-tidspunktet og anvend rollebaseret indholdsfiltrering
                    String rawSnapshot = readBlobAtCommit(repository, commit, fileName);
                    String visibleSnapshot = contentFilter != null
                            ? contentFilter.apply(rawSnapshot, isTherapist)
                            : filterInternalNotes(rawSnapshot, isTherapist);
                    Map<String, String> item = new LinkedHashMap<>();
                    item.put("commitHash", commit.getName().substring(0, 8));
                    item.put("fullHash", commit.getName());
                    item.put("branch", branch);
                    item.put("message", fullMsg);
                    item.put("author", commit.getAuthorIdent().getName());
                    item.put("timestamp", Instant.ofEpochSecond(commit.getCommitTime()).toString());
                    item.put("snapshot", visibleSnapshot);
                    history.add(item);
                }
                historyCache.put(cacheKey, List.copyOf(history));
            } catch (Exception e) {
                log.warn("Could not read Git commit history for patient {}", patientId, e);
            }
            return history;
        }

        public List<Map<String, String>> getCommitHistory(
                String patientId,
                boolean isTherapist,
                BiFunction<String, Boolean, String> contentFilter,
                LocalDate historyStartDate,
                LocalDate historyEndDate,
                String therapistId,
                List<String> allowedBranches
        ) {
            List<Map<String, String>> fullHistory = getCommitHistory(patientId, isTherapist, contentFilter);
            boolean hasBranchFilter = allowedBranches != null && !allowedBranches.isEmpty();
            if (historyStartDate == null && historyEndDate == null && !hasBranchFilter) {
                return fullHistory;
            }

            List<Map<String, String>> filtered = new ArrayList<>();
            for (Map<String, String> item : fullHistory) {
                if (hasBranchFilter) {
                    String commitBranch = item.getOrDefault("branch", "main");
                    if (!allowedBranches.contains(commitBranch)) {
                        continue;
                    }
                }
                String ts = item.get("timestamp");
                if (ts != null) {
                    try {
                        LocalDate commitDate = Instant.parse(ts)
                                .atZone(ZoneId.systemDefault())
                                .toLocalDate();
                        if (historyStartDate != null && commitDate.isBefore(historyStartDate)) {
                            continue;
                        }
                        if (historyEndDate != null && commitDate.isAfter(historyEndDate)) {
                            continue;
                        }
                    } catch (Exception ignored) {
                    }
                }
                filtered.add(item);
            }
            return filtered;
        }

        /**
         * #isPrivateClientBookingCommit(authorName, strippedMessage) — Afgør om et JGit-commit
         * stammer fra klientens kalenderbooking og derfor skal skjules for terapeuter.
         */
        private boolean isPrivateClientBookingCommit(String authorName, String strippedMessage) {
            String messageLower = (strippedMessage != null ? strippedMessage.toLowerCase() : "");
            return PRIVATE_BOOKING_COMMIT_AUTHOR.equalsIgnoreCase(authorName)
                    || messageLower.startsWith("aftale oprettet")
                    || messageLower.startsWith("aftale ombooket")
                    || messageLower.startsWith("aftale aflyst")
                    || messageLower.startsWith("aftale:")
                    || messageLower.startsWith("appointment:")
                    || messageLower.startsWith("appointment created")
                    || messageLower.startsWith("appointment updated")
                    || messageLower.startsWith("appointment cancelled");
        }

        private String readBlobAtCommit(Repository repository, RevCommit commit, String fileName) throws IOException {
            try (TreeWalk treeWalk = new TreeWalk(repository)) {
                treeWalk.addTree(commit.getTree());
                treeWalk.setRecursive(true);
                treeWalk.setFilter(PathFilter.create(fileName));
                if (treeWalk.next()) {
                    ObjectId objectId = treeWalk.getObjectId(0);
                    ObjectLoader loader = repository.open(objectId);
                    return new String(loader.getBytes(), StandardCharsets.UTF_8);
                }
                return "";
            }
        }

        public Git getGit() {
            return git;
        }

        @PreDestroy
        public void close() {
            if (git != null) {
                git.close();
            }
        }
    }

    // =========================================================================
    // 3. REPOSITORIES & PORTS (Package-Private Repositories + Public Port)
    // =========================================================================

    public interface JournalPort {
        List<JournalEntry> findByClientUuidOrderByTimestampDesc(UUID clientUuid);
    }

    @org.springframework.stereotype.Repository
    interface JournalEntryRepository extends BaseRepository<JournalEntry>, JournalPort {
        @Override
        List<JournalEntry> findByClientUuidOrderByTimestampDesc(UUID clientUuid);
    }

    @org.springframework.stereotype.Repository
    interface TherapistClinicalNoteRepository extends BaseRepository<TherapistClinicalNote> {
        List<TherapistClinicalNote> findByClientUuidOrderByCreatedAtDesc(UUID clientUuid);
    }

    @org.springframework.stereotype.Repository
    interface TherapistNoteRepository extends BaseRepository<TherapistNote> {
        List<TherapistNote> findByClientUuid(UUID clientUuid);
    }

    // =========================================================================
    // 4. DOMAIN SERVICES (Package-Private)
    // =========================================================================

    @Service
    static class JournalService extends BaseService<JournalEntry, JournalEntryDto> {

        private JournalEntryRepository journalEntryRepository;
        private JournalGitService journalGitService;
        private TherapistClinicalNoteRepository clinicalNoteRepository;
        private PlatformCacheService cacheService;
        private AppointmentPort appointmentRepository;
        private HandshakePort handshakeRepository;

        @Autowired
        JournalService(
                JournalEntryRepository journalEntryRepository,
                @Autowired(required = false) JournalGitService journalGitService,
                @Autowired(required = false) TherapistClinicalNoteRepository clinicalNoteRepository,
                @Autowired(required = false) PlatformCacheService cacheService,
                @Autowired(required = false) AppointmentPort appointmentRepository,
                @Autowired(required = false) HandshakePort handshakeRepository
        ) {
            super(journalEntryRepository);
            this.journalEntryRepository = journalEntryRepository;
            this.journalGitService = journalGitService;
            this.clinicalNoteRepository = clinicalNoteRepository;
            this.cacheService = Objects.requireNonNullElseGet(cacheService, PlatformCacheService::new);
            this.appointmentRepository = appointmentRepository;
            this.handshakeRepository = handshakeRepository;
        }

        JournalService(JournalEntryRepository journalEntryRepository) {
            this(journalEntryRepository, null, null, new PlatformCacheService(), null, null);
        }

        public String getJournalContent(String rawMarkdown, boolean isTherapist) {
            return filterInternalNotes(rawMarkdown, isTherapist);
        }

        @Override
        protected JournalEntryDto toDto(JournalEntry journalEntryEntity) {
            if (journalEntryEntity == null) {
                return null;
            }
            return new JournalEntryDto(
                    journalEntryEntity.getId(),
                    journalEntryEntity.getTitle(),
                    journalEntryEntity.getContent(),
                    journalEntryEntity.getMoodRating(),
                    journalEntryEntity.getSharedWithTherapists(),
                    journalEntryEntity.getSharedTherapistUuids(),
                    journalEntryEntity.getClientUuid(),
                    journalEntryEntity.getAppointmentId(),
                    journalEntryEntity.getGitBranch(),
                    journalEntryEntity.getTimestamp(),
                    journalEntryEntity.getGitCommitHash()
            );
        }

        @Override
        public JournalEntry toEntity(JournalEntryDto journalEntryDto) {
            if (journalEntryDto == null) {
                return null;
            }
            UUID clientUuid = Objects.requireNonNullElse(
                    journalEntryDto.getClientUuid(),
                    UUID.fromString("00000000-0000-0000-0000-000000000000")
            );
            String title = Objects.requireNonNullElse(journalEntryDto.getTitle(), "Journalindlæg");
            JournalEntry journalEntryEntity = new JournalEntry(
                    title,
                    journalEntryDto.getContent(),
                    Objects.requireNonNullElse(journalEntryDto.getMoodRating(), 7),
                    LocalDateTime.now(),
                    clientUuid
            );
            journalEntryEntity.setId(journalEntryDto.getId());
            journalEntryEntity.setSharedWithTherapists(journalEntryDto.getSharedWithTherapists());
            if (journalEntryDto.getSharedTherapistUuids() != null) {
                journalEntryEntity.setSharedTherapistUuids(journalEntryDto.getSharedTherapistUuids());
            }
            if (journalEntryDto.getAppointmentId() != null) {
                journalEntryEntity.setAppointmentId(journalEntryDto.getAppointmentId());
            }
            journalEntryEntity.setGitBranch(journalEntryDto.getGitBranch());
            return journalEntryEntity;
        }

        @Transactional
        public JournalEntry createJournalEntry(JournalEntryDto dto, Authentication authentication) {
            UUID clientUuid = dto.getClientUuid();
            String authorName = "Client";
            if (authentication != null && authentication.getPrincipal() instanceof Jwt jwt) {
                if (clientUuid == null) {
                    try {
                        clientUuid = UUID.fromString(jwt.getSubject());
                    } catch (IllegalArgumentException e) {
                        clientUuid = UUID.fromString("00000000-0000-0000-0000-000000000000");
                    }
                }
                authorName = Objects.requireNonNullElse(jwt.getClaimAsString("preferred_username"), "Client");
            }
            if (clientUuid == null) {
                clientUuid = UUID.fromString("00000000-0000-0000-0000-000000000000");
            }

            JournalEntryDto effectiveDto = new JournalEntryDto(
                    dto.id(),
                    dto.title(),
                    dto.content(),
                    dto.moodRating(),
                    dto.getSharedWithTherapists(),
                    dto.sharedTherapistUuids(),
                    clientUuid,
                    dto.appointmentId(),
                    dto.getGitBranch()
            );

            JournalEntry entry = toEntity(effectiveDto);
            String title = entry.getTitle();
            String branch = entry.getGitBranch();

            if (journalGitService != null) {
                try {
                    String commitMsg = "Added journal entry: " + title;
                    String patientId = clientUuid.toString();
                    String sharedInfo = Boolean.TRUE.equals(entry.getSharedWithTherapists())
                            ? (entry.getSharedTherapistUuids().isEmpty()
                                    ? "All Active Handshake Therapists"
                                    : entry.getSharedTherapistUuids().toString())
                            : "Private";
                    String aptTrailer = (effectiveDto.getAppointmentId() != null && (effectiveDto.getContent() == null || !effectiveDto.getContent().contains("Refs-Appointment")))
                            ? "\nRefs-Appointment: #" + effectiveDto.getAppointmentId()
                            : "";
                    String content = "## " + title + " (" + LocalDateTime.now() + ")\n"
                            + "Branch: " + branch + " | Mood: " + entry.getMoodRating() + "/10 | Shared: " + sharedInfo + "\n"
                            + effectiveDto.getContent() + aptTrailer;
                    String hash = journalGitService.commitJournalEntryWithHash(patientId, content, commitMsg, authorName, branch);
                    entry.setGitCommitHash(hash);
                } catch (Exception e) {
                    System.err.println("Failed to commit to Git: " + e.getMessage());
                }
            }

            JournalEntry saved = journalEntryRepository.save(entry);
            if (saved != null && saved.getAppointmentId() != null && appointmentRepository != null) {
                appointmentRepository.linkJournalEntry(saved.getAppointmentId(), saved.getId());
            }
            cacheService.put("journal:client:" + clientUuid, saved);
            return saved;
        }

        @Transactional
        public JournalEntry updateJournalEntrySharing(UUID entryId, Map<String, Object> payload) {
            JournalEntry entry = journalEntryRepository.findById(entryId)
                    .orElseThrow(ErrorCode.NOT_FOUND.supply("Journal entry not found"));

            if (payload.containsKey("sharedWithTherapists") && payload.get("sharedWithTherapists") != null) {
                entry.setSharedWithTherapists(Boolean.parseBoolean(String.valueOf(payload.get("sharedWithTherapists"))));
            }
            if (payload.containsKey("sharedTherapistUuids")) {
                Object rawList = payload.get("sharedTherapistUuids");
                if (rawList instanceof List<?> list) {
                    List<UUID> parsed = new ArrayList<>();
                    for (Object item : list) {
                        if (item != null && !String.valueOf(item).isBlank()) {
                            try {
                                parsed.add(UUID.fromString(String.valueOf(item).trim()));
                            } catch (IllegalArgumentException ignored) {
                            }
                        }
                    }
                    entry.setSharedTherapistUuids(parsed);
                } else {
                    entry.setSharedTherapistUuids(List.of());
                }
            }
            if (payload.containsKey("gitBranch") && payload.get("gitBranch") != null) {
                entry.setGitBranch(String.valueOf(payload.get("gitBranch")));
            }

            JournalEntry saved = journalEntryRepository.save(entry);
            if (saved.getClientUuid() != null) {
                cacheService.put("journal:client:" + saved.getClientUuid(), saved);
            }
            return saved;
        }

        @Transactional
        public TherapistClinicalNote createTherapistNote(Map<String, Object> payload, Authentication authentication) {
            if (authentication == null || !(authentication.getPrincipal() instanceof Jwt jwt)) {
                throw ErrorCode.UNAUTHORIZED.toException("Unauthorized");
            }

            ErrorCode.FORBIDDEN.require(
                    hasTherapistRole(authentication),
                    "Access Denied: Required role ROLE_THERAPIST"
            );

            String clientUuidStr = Objects.toString(payload.get("clientUuid"), "");
            String noteText = Objects.toString(payload.getOrDefault("therapistNote", payload.get("content")), "");
            String therapistName = Objects.toString(payload.get("therapistName"), "Clinical Therapist");
            String organisationName = Objects.toString(payload.get("organisationName"), "Associated Clinic");
            UUID appointmentId = null;
            if (payload.get("appointmentId") != null && !String.valueOf(payload.get("appointmentId")).isBlank()) {
                try {
                    appointmentId = UUID.fromString(String.valueOf(payload.get("appointmentId")).trim());
                } catch (IllegalArgumentException ignored) {
                }
            }

            if (!clientUuidStr.isBlank() && !noteText.isBlank()) {
                try {
                    UUID clientUuid = UUID.fromString(clientUuidStr);
                    UUID therapistUuid = parseUuidOrDefault(jwt.getSubject());

                    String aptTrailer = (appointmentId != null && !noteText.contains("Refs-Appointment"))
                            ? "\nRefs-Appointment: #" + appointmentId
                            : "";
                    String formattedInternalBlock = INTERNAL_NOTES_MARKER + "\n"
                            + "**[CONFIDENTIAL CLINICAL NOTE | " + therapistName + " (" + organisationName + ") | " + LocalDateTime.now() + "]**\n"
                            + noteText + aptTrailer;
                    String commitHash = journalGitService != null
                            ? journalGitService.commitJournalEntryWithHash(
                                    clientUuid.toString(),
                                    formattedInternalBlock,
                                    "Clinical note by " + therapistName + " (" + organisationName + ")",
                                    therapistName
                            )
                            : null;

                    if (clinicalNoteRepository != null) {
                        TherapistClinicalNote clinicalNote = new TherapistClinicalNote(
                                clientUuid,
                                therapistUuid,
                                therapistName,
                                organisationName,
                                null,
                                appointmentId,
                                noteText,
                                commitHash
                        );
                        TherapistClinicalNote savedNote = clinicalNoteRepository.save(clinicalNote);
                        if (appointmentId != null && appointmentRepository != null) {
                            appointmentRepository.linkJournalEntry(appointmentId, savedNote.getId());
                        }
                        return savedNote;
                    }
                } catch (Exception ignored) {
                }
            }
            return null;
        }

        public Map<String, Object> getClientJournalBundle(UUID clientUuid, UUID therapistUuid, Authentication auth) {
            boolean isTherapist = hasTherapistRole(auth) || therapistUuid != null;
            UUID callerUuid = Optional.ofNullable(therapistUuid).or(() -> findCallerUuid(auth)).orElse(null);

            LocalDate historyStartDate = null;
            LocalDate historyEndDate = null;
            ClientTherapistHandshake activeHandshake = null;

            if (isTherapist && callerUuid != null && !callerUuid.equals(clientUuid)) {
                if (handshakeRepository != null) {
                    Optional<ClientTherapistHandshake> hsOpt = handshakeRepository
                            .findByClientUuidAndTherapistUuid(clientUuid, callerUuid)
                            .filter(ClientTherapistHandshake::isCurrentlyValid);

                    if (hsOpt.isEmpty()) {
                        return Map.of(
                                "clientUuid", clientUuid,
                                "entries", List.of(),
                                "clinicalNotes", List.of()
                        );
                    }

                    activeHandshake = hsOpt.get();
                    historyStartDate = activeHandshake.findHistoryStartDate().orElse(null);
                    historyEndDate = activeHandshake.findHistoryEndDate().orElse(null);
                }
            }

            List<JournalEntry> entries = journalEntryRepository.findByClientUuidOrderByTimestampDesc(clientUuid);
            if (isTherapist && callerUuid != null && !clientUuid.equals(callerUuid)) {
                final ClientTherapistHandshake hsFilter = activeHandshake;
                entries = entries.stream()
                        .filter(e -> e.isVisibleToTherapist(callerUuid))
                        .filter(e -> hsFilter == null || hsFilter.isBranchAllowed(e.getGitBranch()))
                        .collect(Collectors.toList());
            } else if (isTherapist) {
                entries = entries.stream()
                        .filter(JournalEntry::getSharedWithTherapists)
                        .collect(Collectors.toList());
            }

            if (isTherapist && (historyStartDate != null || historyEndDate != null)) {
                final LocalDate fStart = historyStartDate;
                final LocalDate fEnd = historyEndDate;
                entries = entries.stream().filter(e -> {
                    if (e.getTimestamp() == null) return true;
                    LocalDate entryDate = e.getTimestamp().toLocalDate();
                    if (fStart != null && entryDate.isBefore(fStart)) return false;
                    if (fEnd != null && entryDate.isAfter(fEnd)) return false;
                    return true;
                }).collect(Collectors.toList());
            }

            if (isTherapist && activeHandshake != null && activeHandshake.hasCustomRestriction("NO_MOOD_SCORES")) {
                List<JournalEntry> maskedEntries = new ArrayList<>();
                for (JournalEntry orig : entries) {
                    boolean maskMood = activeHandshake.hasRestrictionForBranch(orig.getGitBranch(), "NO_MOOD_SCORES");
                    JournalEntry copy = new JournalEntry(
                            orig.getTitle(),
                            orig.getContent(),
                            maskMood ? null : orig.getMoodRating(),
                            orig.getTimestamp(),
                            orig.getClientUuid()
                    );
                    copy.setId(orig.getId());
                    copy.setSharedWithTherapists(orig.getSharedWithTherapists());
                    copy.setSharedTherapistUuids(orig.getSharedTherapistUuids());
                    copy.setGitCommitHash(orig.getGitCommitHash());
                    copy.setGitBranch(orig.getGitBranch());
                    copy.setAppointmentId(orig.getAppointmentId());
                    maskedEntries.add(copy);
                }
                entries = maskedEntries;
            }

            List<TherapistClinicalNote> clinicalNotes = (isTherapist && clinicalNoteRepository != null)
                    ? clinicalNoteRepository.findByClientUuidOrderByCreatedAtDesc(clientUuid)
                    : List.of();

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("clientUuid", clientUuid);
            result.put("entries", entries);
            result.put("clinicalNotes", clinicalNotes);
            if (activeHandshake != null) {
                result.put("allowedBranches", activeHandshake.getAllowedBranchesList());
                result.put("customRestrictions", activeHandshake.getCustomRestrictionsList());
                result.put("branchRestrictions", activeHandshake.getBranchRestrictionsMap());
            }
            if (historyStartDate != null || historyEndDate != null) {
                result.put("historyFilterActive", true);
                result.put("historyStartDate", historyStartDate != null ? historyStartDate.toString() : null);
                result.put("historyEndDate", historyEndDate != null ? historyEndDate.toString() : null);
            }
            return result;
        }

        public List<Map<String, String>> getJournalCommitHistory(UUID clientUuid, UUID therapistUuid, Authentication auth) {
            boolean isTherapist = hasTherapistRole(auth) || therapistUuid != null;
            UUID callerUuid = Optional.ofNullable(therapistUuid).or(() -> findCallerUuid(auth)).orElse(null);

            LocalDate historyStartDate = null;
            LocalDate historyEndDate = null;
            List<String> allowedBranches = null;

            if (isTherapist && callerUuid != null && !callerUuid.equals(clientUuid)) {
                if (handshakeRepository != null) {
                    Optional<ClientTherapistHandshake> hsOpt = handshakeRepository
                            .findByClientUuidAndTherapistUuid(clientUuid, callerUuid)
                            .filter(ClientTherapistHandshake::isCurrentlyValid);

                    if (hsOpt.isEmpty()) {
                        return List.of();
                    }

                    ClientTherapistHandshake hs = hsOpt.get();
                    historyStartDate = hs.findHistoryStartDate().orElse(null);
                    historyEndDate = hs.findHistoryEndDate().orElse(null);
                    allowedBranches = hs.getAllowedBranchesList();
                }
            }

            return journalGitService.getCommitHistory(
                    clientUuid.toString(),
                    isTherapist,
                    this::getJournalContent,
                    historyStartDate,
                    historyEndDate,
                    callerUuid != null ? callerUuid.toString() : null,
                    allowedBranches
            );
        }

        public List<Map<String, String>> getClientTimeline(UUID clientUuid, Authentication auth) {
            boolean isTherapist = hasTherapistRole(auth);
            String rawMarkdown = journalGitService.readJournalEntry(clientUuid.toString());
            String content = getJournalContent(rawMarkdown, isTherapist);
            return List.of(Map.of("id", clientUuid.toString(), "content", content));
        }

        public boolean hasTherapistRole(Authentication auth) {
            if (auth == null) {
                return false;
            }
            if (auth.getAuthorities() != null && auth.getAuthorities().stream()
                    .anyMatch(a -> "ROLE_therapist".equalsIgnoreCase(a.getAuthority())
                            || "ROLE_ROLE_THERAPIST".equalsIgnoreCase(a.getAuthority()))) {
                return true;
            }
            if (auth.getPrincipal() instanceof Jwt jwt) {
                Map<String, Object> realmAccess = jwt.getClaimAsMap("realm_access");
                if (realmAccess != null && realmAccess.get("roles") instanceof List<?> roles) {
                    return roles.stream().anyMatch(r ->
                            "therapist".equalsIgnoreCase(String.valueOf(r))
                                    || "ROLE_THERAPIST".equalsIgnoreCase(String.valueOf(r)));
                }
            }
            return false;
        }

        public Optional<UUID> findCallerUuid(Authentication auth) {
            return Optional.ofNullable(auth)
                    .map(Authentication::getPrincipal)
                    .filter(Jwt.class::isInstance)
                    .map(Jwt.class::cast)
                    .map(Jwt::getSubject)
                    .flatMap(sub -> {
                        try {
                            return Optional.of(UUID.fromString(sub));
                        } catch (IllegalArgumentException ignored) {
                            return Optional.empty();
                        }
                    });
        }

        public UUID extractCallerUuid(Authentication auth) {
            return findCallerUuid(auth).orElse(null);
        }

        public UUID parseUuidOrDefault(String sub) {
            return Optional.ofNullable(sub)
                    .flatMap(s -> {
                        try {
                            return Optional.of(UUID.fromString(s));
                        } catch (IllegalArgumentException ex) {
                            return Optional.empty();
                        }
                    })
                    .orElseGet(() -> UUID.fromString("6ba7b810-9dad-11d1-80b4-00c04fd430c8"));
        }
    }

    // =========================================================================
    // 5. REST CONTROLLERS (Package-Private)
    // =========================================================================

    @RestController
    @RequestMapping("/api/v1/journals")
    @CrossOrigin(origins = "*")
    static class JournalController extends BaseController<JournalEntry, JournalEntryDto> {

        private final JournalService journalService;

        JournalController(JournalService journalService) {
            super(journalService);
            this.journalService = journalService;
        }

        @Override
        @PostMapping
        public ResponseEntity<JournalEntryDto> create(@RequestBody JournalEntryDto dto) {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            JournalEntry saved = journalService.createJournalEntry(dto, auth);
            return ResponseEntity.ok(journalService.toDto(saved));
        }

        public ResponseEntity<JournalEntry> createJournalEntry(JournalEntryDto dto, Authentication authentication) {
            return ResponseEntity.ok(journalService.createJournalEntry(dto, authentication));
        }

        @PatchMapping("/{entryId}/sharing")
        public ResponseEntity<JournalEntry> updateJournalEntrySharing(
                @PathVariable UUID entryId,
                @RequestBody Map<String, Object> payload,
                Authentication authentication
        ) {
            return ResponseEntity.ok(journalService.updateJournalEntrySharing(entryId, payload));
        }

        @PostMapping("/therapist-notes")
        public ResponseEntity<?> createTherapistNote(@RequestBody Map<String, Object> payload, Authentication authentication) {
            TherapistClinicalNote savedNote = journalService.createTherapistNote(payload, authentication);
            if (savedNote != null) {
                return ResponseEntity.ok(savedNote);
            }
            return ResponseEntity.ok().build();
        }

        @GetMapping("/client/{clientUuid}")
        public ResponseEntity<Map<String, Object>> getClientJournalBundle(
                @PathVariable UUID clientUuid,
                @RequestParam(required = false) UUID therapistUuid,
                Authentication auth
        ) {
            return ResponseEntity.ok(journalService.getClientJournalBundle(clientUuid, therapistUuid, auth));
        }

        @GetMapping("/history/{clientUuid}")
        public ResponseEntity<List<Map<String, String>>> getJournalCommitHistory(
                @PathVariable UUID clientUuid,
                @RequestParam(required = false) UUID therapistUuid,
                Authentication auth
        ) {
            return ResponseEntity.ok(journalService.getJournalCommitHistory(clientUuid, therapistUuid, auth));
        }

        public ResponseEntity<Map<String, Object>> getClientJournalBundle(UUID clientUuid, Authentication auth) {
            return getClientJournalBundle(clientUuid, null, auth);
        }

        public ResponseEntity<List<Map<String, String>>> getJournalCommitHistory(UUID clientUuid, Authentication auth) {
            return getJournalCommitHistory(clientUuid, null, auth);
        }

        @GetMapping("/timeline/{clientUuid}")
        public ResponseEntity<List<Map<String, String>>> getClientTimeline(
                @PathVariable UUID clientUuid,
                Authentication auth
        ) {
            try {
                return ResponseEntity.ok(journalService.getClientTimeline(clientUuid, auth));
            } catch (Exception e) {
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
            }
        }
    }
}
