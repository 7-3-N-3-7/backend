package com.platform.service;

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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
public class JournalGitService {

    private static final Logger log = LoggerFactory.getLogger(JournalGitService.class);

    @Value("${journal.git.repo-path:./journal-repo}")
    private String repoPath;

    private Git git;

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

    public List<Map<String, String>> getCommitHistory(String patientId, boolean isTherapist, JournalService journalService) {
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
        } catch (Exception e) {
            log.warn("Could not read Git commit history for patient {}", patientId, e);
        }
        return history;
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

    @jakarta.annotation.PreDestroy
    public void close() {
        if (git != null) {
            git.close();
        }
    }
}
