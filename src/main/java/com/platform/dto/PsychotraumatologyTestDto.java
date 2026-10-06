package com.platform.dto;

import java.util.UUID;

public class PsychotraumatologyTestDto {
    private UUID clientUuid;
    private UUID therapistUuid;
    private String testType;
    private Integer score;
    private String severity;
    private String answersJson;

    public PsychotraumatologyTestDto() {}

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
