package com.platform.dto;

<<<<<<< HEAD
import com.platform.core.dto.BaseDTO;
import java.util.UUID;

public class PsychotraumatologyTestDto implements BaseDTO<UUID> {
    private UUID id;
=======
import java.util.UUID;

public class PsychotraumatologyTestDto {
>>>>>>> 3d3aa53f18dac79b5509f233401d317d3db0787d
    private UUID clientUuid;
    private UUID therapistUuid;
    private String testType;
    private Integer score;
    private String severity;
    private String answersJson;

    public PsychotraumatologyTestDto() {}

<<<<<<< HEAD
    @Override
    public UUID getId() { return id; }

    @Override
    public void setId(UUID id) { this.id = id; }

=======
>>>>>>> 3d3aa53f18dac79b5509f233401d317d3db0787d
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
}
