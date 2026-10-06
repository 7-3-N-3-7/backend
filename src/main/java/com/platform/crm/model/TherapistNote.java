package com.platform.crm.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "therapist_notes")
public class TherapistNote {

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

    public UUID getId() { return id; }
    public UUID getClientUuid() { return clientUuid; }
    public UUID getTherapistUuid() { return therapistUuid; }
    public String getNote() { return note; }
    public LocalDateTime getTimestamp() { return timestamp; }
}
