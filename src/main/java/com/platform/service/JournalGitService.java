package com.platform.service;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

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

    public synchronized void commitJournalEntry(String patientId, String entryContent, String commitMessage) {
        try {
            File entryFile = new File(repoPath, patientId + ".md");
            Files.writeString(entryFile.toPath(), entryContent + "\n\n", 
                java.nio.file.StandardOpenOption.CREATE, 
                java.nio.file.StandardOpenOption.APPEND);

            git.add().addFilepattern(patientId + ".md").call();
            git.commit().setMessage(commitMessage).call();
            
            log.info("Committed journal entry for patient {}", patientId);
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
