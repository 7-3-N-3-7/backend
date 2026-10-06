package com.platform.dto;

import java.util.UUID;

public class JournalEntryDto {
    private String title;
    private String content;
    private Integer moodRating;

    public JournalEntryDto() {}

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    
    public Integer getMoodRating() { return moodRating; }
    public void setMoodRating(Integer moodRating) { this.moodRating = moodRating; }
}
