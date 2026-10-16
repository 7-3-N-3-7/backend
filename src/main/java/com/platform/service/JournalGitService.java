package com.platform.service;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
<<<<<<< HEAD
<<<<<<< HEAD
<<<<<<< HEAD
=======
>>>>>>> 16f2191da8c19882528cd7f0f6ff79480a370715
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectLoader;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.eclipse.jgit.treewalk.filter.PathFilter;
=======
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
>>>>>>> 3d3aa53f18dac79b5509f233401d317d3db0787d
<<<<<<< HEAD
=======
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
>>>>>>> 266b19092e0a4e40a20f5cd2eaf04be642298e92
=======
>>>>>>> 16f2191da8c19882528cd7f0f6ff79480a370715
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.io.File;
import java.io.IOException;
<<<<<<< HEAD
<<<<<<< HEAD
<<<<<<< HEAD
=======
>>>>>>> 16f2191da8c19882528cd7f0f6ff79480a370715
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
=======
import java.nio.file.Files;
import java.nio.file.Path;
>>>>>>> 3d3aa53f18dac79b5509f233401d317d3db0787d
<<<<<<< HEAD
=======
import java.nio.file.Files;
import java.nio.file.Path;
>>>>>>> 266b19092e0a4e40a20f5cd2eaf04be642298e92
=======
>>>>>>> 16f2191da8c19882528cd7f0f6ff79480a370715

@Service
public class JournalGitService {

    private static final Logger log = LoggerFactory.getLogger(JournalGitService.class);

    @Value("${journal.git.repo-path:./journal-repo}")
    private String repoPath;

    private Git git;
<<<<<<< HEAD
<<<<<<< HEAD
<<<<<<< HEAD
    private final Map<String, List<Map<String, String>>> historyCache = new java.util.concurrent.ConcurrentHashMap<>();
=======
>>>>>>> 3d3aa53f18dac79b5509f233401d317d3db0787d
=======
>>>>>>> 266b19092e0a4e40a20f5cd2eaf04be642298e92
=======
    private final Map<String, List<Map<String, String>>> historyCache = new java.util.concurrent.ConcurrentHashMap<>();
=======
>>>>>>> 3d3aa53f18dac79b5509f233401d317d3db0787d
>>>>>>> 16f2191da8c19882528cd7f0f6ff79480a370715

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

<<<<<<< HEAD
<<<<<<< HEAD
<<<<<<< HEAD
<<<<<<< HEAD
=======
>>>>>>> 16f2191da8c19882528cd7f0f6ff79480a370715
    public void commitJournalEntry(String patientId, String entryContent, String commitMessage) {
        commitJournalEntryWithHash(patientId, entryContent, commitMessage, "Platform Audit");
    }

    public String commitJournalEntryWithHash(String patientId, String entryContent, String commitMessage, String authorName) {
=======
    public synchronized void commitJournalEntry(String patientId, String entryContent, String commitMessage) {
>>>>>>> fa4972dbc8a433d3937f53daa44e44c3ed6b905a
        try {
            File entryFile = new File(repoPath, patientId + ".md");
            Files.writeString(entryFile.toPath(), entryContent + "\n\n",
                java.nio.file.StandardOpenOption.CREATE,
                java.nio.file.StandardOpenOption.APPEND);

            git.add().addFilepattern(patientId + ".md").call();
            RevCommit commit = git.commit()
                    .setSign(false)
                    .setAuthor(Objects.requireNonNullElse(authorName, "Platform Audit"), "audit@platform.local")
                    .setMessage(commitMessage)
                    .call();

            log.info("Committed journal entry for patient {}", patientId);
            historyCache.keySet().removeIf(key -> key.startsWith(patientId + ":"));
            return commit.getName().substring(0, 8);
=======
<<<<<<< HEAD
=======
>>>>>>> 266b19092e0a4e40a20f5cd2eaf04be642298e92
=======
>>>>>>> 16f2191da8c19882528cd7f0f6ff79480a370715
    public synchronized void commitJournalEntry(String patientId, String entryContent, String commitMessage) {
        try {
            File entryFile = new File(repoPath, patientId + ".md");
            Files.writeString(entryFile.toPath(), entryContent + "\n\n", 
                java.nio.file.StandardOpenOption.CREATE, 
                java.nio.file.StandardOpenOption.APPEND);

            git.add().addFilepattern(patientId + ".md").call();
            git.commit().setMessage(commitMessage).call();
            
            log.info("Committed journal entry for patient {}", patientId);
<<<<<<< HEAD
<<<<<<< HEAD
>>>>>>> 3d3aa53f18dac79b5509f233401d317d3db0787d
=======
>>>>>>> 266b19092e0a4e40a20f5cd2eaf04be642298e92
=======
>>>>>>> 3d3aa53f18dac79b5509f233401d317d3db0787d
>>>>>>> 16f2191da8c19882528cd7f0f6ff79480a370715
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

<<<<<<< HEAD
<<<<<<< HEAD
<<<<<<< HEAD
=======
>>>>>>> 16f2191da8c19882528cd7f0f6ff79480a370715
    public List<Map<String, String>> getCommitHistory(String patientId, boolean isTherapist, JournalService journalService) {
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
                // Strict client privacy: appointments are private and personal to the client.
                // Raw appointment commits (from "Klinik Booking" or appointment lifecycle messages)
                // must not be exposed to therapists.
                if (isTherapist) {
                    String author = commit.getAuthorIdent() != null ? commit.getAuthorIdent().getName() : "";
                    String msg = commit.getFullMessage();
                    String msgLower = (msg != null ? msg.toLowerCase() : "");
                    if ("Klinik Booking".equalsIgnoreCase(author)
                            || msgLower.startsWith("aftale oprettet")
                            || msgLower.startsWith("aftale ombooket")
                            || msgLower.startsWith("aftale aflyst")
                            || msgLower.startsWith("aftale:")
                            || msgLower.startsWith("appointment:")
                            || msgLower.startsWith("appointment created")
                            || msgLower.startsWith("appointment updated")
                            || msgLower.startsWith("appointment cancelled")) {
                        continue;
                    }
                }

                String rawSnapshot = readBlobAtCommit(repository, commit, fileName);
                String visibleSnapshot = journalService.getJournalContent(rawSnapshot, isTherapist);
                Map<String, String> item = new LinkedHashMap<>();
                item.put("commitHash", commit.getName().substring(0, 8));
                item.put("fullHash", commit.getName());
                item.put("message", commit.getFullMessage());
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
            JournalService journalService,
            java.time.LocalDate historyStartDate,
            java.time.LocalDate historyEndDate,
            String therapistId
    ) {
        List<Map<String, String>> fullHistory = getCommitHistory(patientId, isTherapist, journalService);
        if (historyStartDate == null && historyEndDate == null) {
            return fullHistory;
        }

        List<Map<String, String>> filtered = new ArrayList<>();
        for (Map<String, String> item : fullHistory) {
            String ts = item.get("timestamp");
            if (ts != null) {
                try {
                    java.time.LocalDate commitDate = java.time.Instant.parse(ts)
                            .atZone(java.time.ZoneId.systemDefault())
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

=======
>>>>>>> 3d3aa53f18dac79b5509f233401d317d3db0787d
<<<<<<< HEAD
=======
>>>>>>> 266b19092e0a4e40a20f5cd2eaf04be642298e92
=======
>>>>>>> 16f2191da8c19882528cd7f0f6ff79480a370715
    public Git getGit() {
        return git;
    }

    @jakarta.annotation.PreDestroy
    public void close() {
        if (git != null) {
            git.close();
        }
    }
}
