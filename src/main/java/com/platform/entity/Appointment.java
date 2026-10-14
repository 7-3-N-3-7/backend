package com.platform.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "appointments")
public class Appointment {

    public Appointment() {}

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private UUID clientUuid;

    @Column(nullable = false)
    private UUID therapistUuid;

    @Column(name = "service_id")
    private UUID serviceId;

    @Column(name = "therapist_name")
    private String therapistName;

    @Column(name = "organisation_name")
    private String organisationName;

    @Column
    private String location;

    @Column(nullable = false)
    private LocalDateTime startTime;

    @Column(nullable = false)
    private LocalDateTime endTime;

    @Column
    private String title;

    @Column
    private String status;

    public Appointment(
        UUID clientUuid,
        UUID therapistUuid,
        LocalDateTime startTime,
        LocalDateTime endTime,
        String title,
        String status
    ) {
        this.clientUuid = clientUuid;
        this.therapistUuid = therapistUuid;
        this.startTime = startTime;
        this.endTime = endTime;
        this.title = title;
        this.status = status;
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public UUID getClientUuid() { return clientUuid; }
    public void setClientUuid(UUID clientUuid) { this.clientUuid = clientUuid; }

    public UUID getTherapistUuid() { return therapistUuid; }
    public void setTherapistUuid(UUID therapistUuid) { this.therapistUuid = therapistUuid; }

    public UUID getServiceId() { return serviceId; }
    public void setServiceId(UUID serviceId) { this.serviceId = serviceId; }

    public String getTherapistName() { return therapistName; }
    public void setTherapistName(String therapistName) { this.therapistName = therapistName; }

    public String getOrganisationName() { return organisationName; }
    public void setOrganisationName(String organisationName) { this.organisationName = organisationName; }

    public String getLocation() { return location; }
    public void setLocation(String location) { this.location = location; }

    public LocalDateTime getStartTime() { return startTime; }
    public void setStartTime(LocalDateTime startTime) { this.startTime = startTime; }

    public LocalDateTime getEndTime() { return endTime; }
    public void setEndTime(LocalDateTime endTime) { this.endTime = endTime; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
}
