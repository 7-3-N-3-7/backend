package com.platform.crm.model;

import jakarta.persistence.*;
import java.util.UUID;
import java.time.LocalDateTime;

@Entity
@Table(name = "psychotraumatology_tests")
public class PsychotraumatologyTest {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "client_uuid", nullable = false)
    private UUID clientUuid;

    @Column(name = "therapist_uuid", nullable = false)
    private UUID therapistUuid;

    @Column(name = "test_type", nullable = false)
    private String testType;

    @Column(nullable = false)
    private Integer score;

    @Column(nullable = false)
    private String severity;

    @Column(name = "answers_json", columnDefinition = "TEXT", nullable = false)
    private String answersJson;

    @Column(nullable = false)
    private LocalDateTime timestamp;

    public PsychotraumatologyTest() {}

    @PrePersist
    protected void onCreate() {
        if (timestamp == null) {
            timestamp = LocalDateTime.now();
        }
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public UUID getClientUuid() { return clientUuid; }
    public void setClientUuid(UUID clientUuid) { this.clientUuid = clientUuid; }

    public UUID getTherapistUuid() { return therapistUuid; }
    public void setTherapistUuid(UUID therapistUuid) { this.therapistUuid = therapistUuid; }

    public String getTestType() { return testType; }
    public void setTestType(String testType) { this.testType = testType; }

    public Integer getScore() { return score; }
    public void setScore(Integer score) { this.score = score; }

    public String getSeverity() { return severity; }
    public void setSeverity(String severity) { this.severity = severity; }

    public String getAnswersJson() { return answersJson; }
    public void setAnswersJson(String answersJson) { this.answersJson = answersJson; }

    public LocalDateTime getTimestamp() { return timestamp; }
    public void setTimestamp(LocalDateTime timestamp) { this.timestamp = timestamp; }
}
