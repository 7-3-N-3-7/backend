package com.platform.crm.model;

import com.platform.core.entity.BaseEntity;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "therapist_clinical_notes")
public class TherapistClinicalNote extends BaseEntity<UUID> {

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

    public TherapistClinicalNote(UUID clientUuid, UUID authorTherapistUuid, String authorTherapistName, String organisationName, UUID journalEntryId, String confidentialMarkdown, String gitCommitHash) {
        this(clientUuid, authorTherapistUuid, authorTherapistName, organisationName, journalEntryId, null, confidentialMarkdown, gitCommitHash);
    }

    public TherapistClinicalNote(UUID clientUuid, UUID authorTherapistUuid, String authorTherapistName, String organisationName, UUID journalEntryId, UUID appointmentId, String confidentialMarkdown, String gitCommitHash) {
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

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

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
