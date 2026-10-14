package com.platform.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "client_therapist_handshakes")
public class ClientTherapistHandshake {

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
    private String status; // PENDING, ACTIVE, REVOKED

    @Column(name = "granted_at")
    private LocalDateTime grantedAt;

    @Column(name = "revoked_at")
    private LocalDateTime revokedAt;

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

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public UUID getClientUuid() { return clientUuid; }
    public void setClientUuid(UUID clientUuid) { this.clientUuid = clientUuid; }

    public String getClientName() { return clientName; }
    public void setClientName(String clientName) { this.clientName = clientName; }

    public UUID getTherapistUuid() { return therapistUuid; }
    public void setTherapistUuid(UUID therapistUuid) { this.therapistUuid = therapistUuid; }

    public String getTherapistName() { return therapistName; }
    public void setTherapistName(String therapistName) { this.therapistName = therapistName; }

    public String getOrganisationName() { return organisationName; }
    public void setOrganisationName(String organisationName) { this.organisationName = organisationName; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public LocalDateTime getGrantedAt() { return grantedAt; }
    public void setGrantedAt(LocalDateTime grantedAt) { this.grantedAt = grantedAt; }

    public LocalDateTime getRevokedAt() { return revokedAt; }
    public void setRevokedAt(LocalDateTime revokedAt) { this.revokedAt = revokedAt; }
}
