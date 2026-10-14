package com.platform.dto;

import java.util.UUID;

public class JournalEntryDto {
    private String title;
    private String content;
    private Integer moodRating;
    private Boolean sharedWithTherapists = true;
    private UUID clientUuid;

    public JournalEntryDto() {}

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    
    public Integer getMoodRating() { return moodRating; }
    public void setMoodRating(Integer moodRating) { this.moodRating = moodRating; }

    public Boolean getSharedWithTherapists() { return sharedWithTherapists != null ? sharedWithTherapists : true; }
    public void setSharedWithTherapists(Boolean sharedWithTherapists) { this.sharedWithTherapists = sharedWithTherapists; }

    public UUID getClientUuid() { return clientUuid; }
    public void setClientUuid(UUID clientUuid) { this.clientUuid = clientUuid; }
}
