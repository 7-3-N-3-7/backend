package com.platform.crm.model;
    
import com.platform.core.entity.BaseEntity;
import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "journal_entries")
public class JournalEntry extends BaseEntity<java.util.UUID> {

    @Column(nullable = false, length = 255)
    private String title;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    @Column(nullable = true)
    private Integer moodRating;

    @Column(nullable = false)
    private LocalDateTime timestamp;

    @Column(name = "client_uuid", nullable = false)
    private java.util.UUID clientUuid;

    @Column(name = "is_shared_with_therapists")
    private Boolean sharedWithTherapists = true;

    @Column(name = "shared_therapist_uuids", columnDefinition = "TEXT")
    private String sharedTherapistUuidsCsv;

    @Column(name = "git_commit_hash")
    private String gitCommitHash;

    @Column(name = "appointment_id")
    private java.util.UUID appointmentId;

    public JournalEntry() {}

    public JournalEntry(    
        String title,
        String content, 
        Integer moodRating,
        LocalDateTime timestamp, 
        java.util.UUID clientUuid
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

    public java.util.UUID getId() { return id; }
    public void setId(java.util.UUID id) { this.id = id; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }

    public Integer getMoodRating() { return moodRating; }
    public void setMoodRating(Integer moodRating) { this.moodRating = moodRating; }

    public LocalDateTime getTimestamp() { return timestamp; }
    public void setTimestamp(LocalDateTime timestamp) { this.timestamp = timestamp; }

    public java.util.UUID getClientUuid() { return clientUuid; }
    public void setClientUuid(java.util.UUID clientUuid) { this.clientUuid = clientUuid; }

    public Boolean getSharedWithTherapists() { return sharedWithTherapists != null ? sharedWithTherapists : true; }
    public void setSharedWithTherapists(Boolean sharedWithTherapists) { this.sharedWithTherapists = sharedWithTherapists; }

    public java.util.List<java.util.UUID> getSharedTherapistUuids() {
        if (sharedTherapistUuidsCsv == null || sharedTherapistUuidsCsv.isBlank()) {
            return java.util.List.of();
        }
        java.util.List<java.util.UUID> list = new java.util.ArrayList<>();
        for (String part : sharedTherapistUuidsCsv.split(",")) {
            String trimmed = part.trim();
            if (!trimmed.isEmpty()) {
                try {
                    list.add(java.util.UUID.fromString(trimmed));
                } catch (IllegalArgumentException ignored) {
                }
            }
        }
        return list;
    }

    public void setSharedTherapistUuids(java.util.List<java.util.UUID> uuids) {
        if (uuids == null || uuids.isEmpty()) {
            this.sharedTherapistUuidsCsv = null;
        } else {
            this.sharedTherapistUuidsCsv = uuids.stream()
                    .filter(java.util.Objects::nonNull)
                    .distinct()
                    .map(java.util.UUID::toString)
                    .collect(java.util.stream.Collectors.joining(","));
        }
    }

    public boolean isVisibleToTherapist(java.util.UUID therapistUuid) {
        if (!Boolean.TRUE.equals(getSharedWithTherapists())) {
            return false;
        }
        java.util.List<java.util.UUID> allowed = getSharedTherapistUuids();
        if (allowed.isEmpty()) {
            return true;
        }
        return therapistUuid != null && allowed.contains(therapistUuid);
    }

    public String getGitCommitHash() { return gitCommitHash; }
    public void setGitCommitHash(String gitCommitHash) { this.gitCommitHash = gitCommitHash; }

    public java.util.UUID getAppointmentId() { return appointmentId; }
    public void setAppointmentId(java.util.UUID appointmentId) { this.appointmentId = appointmentId; }
}
