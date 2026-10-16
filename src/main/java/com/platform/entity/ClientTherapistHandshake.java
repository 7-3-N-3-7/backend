package com.platform.entity;

import com.platform.core.entity.BaseEntity;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "client_therapist_handshakes")
public class ClientTherapistHandshake extends BaseEntity<UUID> {

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

    @Column(name = "valid_from")
    private java.time.LocalDate validFrom;

    @Column(name = "valid_to")
    private java.time.LocalDate validTo;

    @Column(name = "history_start_date")
    private java.time.LocalDate historyStartDate;

    @Column(name = "history_end_date")
    private java.time.LocalDate historyEndDate;

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

    public java.time.LocalDate getValidFrom() { return validFrom; }
    public void setValidFrom(java.time.LocalDate validFrom) { this.validFrom = validFrom; }

    public java.time.LocalDate getValidTo() { return validTo; }
    public void setValidTo(java.time.LocalDate validTo) { this.validTo = validTo; }

    public java.time.LocalDate getHistoryStartDate() { return historyStartDate; }
    public void setHistoryStartDate(java.time.LocalDate historyStartDate) { this.historyStartDate = historyStartDate; }

    public java.time.LocalDate getHistoryEndDate() { return historyEndDate; }
    public void setHistoryEndDate(java.time.LocalDate historyEndDate) { this.historyEndDate = historyEndDate; }

    public boolean isCurrentlyValid() {
        if (!"ACTIVE".equalsIgnoreCase(status)) return false;
        java.time.LocalDate today = java.time.LocalDate.now();
        if (validFrom != null && today.isBefore(validFrom)) return false;
        if (validTo != null && today.isAfter(validTo)) return false;
        return true;
    }

    public boolean isCommitInHistoricalRange(java.time.Instant commitTime) {
        if (commitTime == null) return true;
        java.time.LocalDate commitDate = commitTime.atZone(java.time.ZoneId.systemDefault()).toLocalDate();
        if (historyStartDate != null && commitDate.isBefore(historyStartDate)) return false;
        if (historyEndDate != null && commitDate.isAfter(historyEndDate)) return false;
        return true;
    }
}
