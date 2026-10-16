package com.platform.crm.model;

<<<<<<< HEAD
import com.platform.core.entity.BaseEntity;
=======
<<<<<<< HEAD
<<<<<<< HEAD
<<<<<<< HEAD
import com.platform.core.entity.BaseEntity;
=======
>>>>>>> 3d3aa53f18dac79b5509f233401d317d3db0787d
=======
>>>>>>> 266b19092e0a4e40a20f5cd2eaf04be642298e92
=======
import com.platform.core.entity.BaseEntity;
=======
>>>>>>> 3d3aa53f18dac79b5509f233401d317d3db0787d
>>>>>>> 16f2191da8c19882528cd7f0f6ff79480a370715
>>>>>>> d6bd62f49cdd26c1f0dfc9614c1ba49cc9f83309
import jakarta.persistence.*;
import java.util.UUID;
import java.time.LocalDateTime;

@Entity
@Table(name = "psychotraumatology_tests")
<<<<<<< HEAD
public class PsychotraumatologyTest extends BaseEntity<UUID> {
=======
<<<<<<< HEAD
<<<<<<< HEAD
<<<<<<< HEAD
public class PsychotraumatologyTest extends BaseEntity<UUID> {
=======
=======
>>>>>>> 266b19092e0a4e40a20f5cd2eaf04be642298e92
=======
public class PsychotraumatologyTest extends BaseEntity<UUID> {
=======
>>>>>>> 16f2191da8c19882528cd7f0f6ff79480a370715
public class PsychotraumatologyTest {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
<<<<<<< HEAD
<<<<<<< HEAD
>>>>>>> 3d3aa53f18dac79b5509f233401d317d3db0787d
=======
>>>>>>> 266b19092e0a4e40a20f5cd2eaf04be642298e92
=======
>>>>>>> 3d3aa53f18dac79b5509f233401d317d3db0787d
>>>>>>> 16f2191da8c19882528cd7f0f6ff79480a370715
>>>>>>> d6bd62f49cdd26c1f0dfc9614c1ba49cc9f83309

    @Column(name = "client_uuid", nullable = false)
    private UUID clientUuid;

<<<<<<< HEAD
    @Column(name = "therapist_uuid", nullable = true)
=======
<<<<<<< HEAD
<<<<<<< HEAD
<<<<<<< HEAD
=======
>>>>>>> 16f2191da8c19882528cd7f0f6ff79480a370715
    @Column(name = "therapist_uuid", nullable = true)
=======
    @Column(name = "therapist_uuid", nullable = false)
>>>>>>> 3d3aa53f18dac79b5509f233401d317d3db0787d
<<<<<<< HEAD
=======
    @Column(name = "therapist_uuid", nullable = false)
>>>>>>> 266b19092e0a4e40a20f5cd2eaf04be642298e92
=======
>>>>>>> 16f2191da8c19882528cd7f0f6ff79480a370715
>>>>>>> d6bd62f49cdd26c1f0dfc9614c1ba49cc9f83309
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
