package com.platform.crm.model;
    
import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "journal_entries")
public class JournalEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private java.util.UUID id;

    @Column(nullable = false, length = 255)
    private String title;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    @Column(nullable = true)
    private Integer moodRating;

    @Column(nullable = false)
    private LocalDateTime timestamp;

    @Column(name = "client_uuid", nullable = false)
    private java.util.UUID clientUuid;

    public JournalEntry() {}

    public JournalEntry
    (    
        String           title,
        String           content, 
        Integer          moodRating,
        LocalDateTime    timestamp, 
        java.util.UUID   clientUuid)
    {
        this.title     = title;
        this.content   = content;
        this.moodRating = moodRating;
        this.timestamp = timestamp;
        this.clientUuid = clientUuid;
    }

    @PrePersist
    protected void onCreate() {
        if (timestamp == null) {
            timestamp = LocalDateTime.now();
        }
    }

    public java.util.UUID getId() { return id; }
    public void setId(java.util.UUID id) { this.id = id; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }

    public Integer getMoodRating() { return moodRating; }
    public void setMoodRating(Integer moodRating) { this.moodRating = moodRating; }

    public LocalDateTime getTimestamp() { return timestamp; }
    public void setTimestamp(LocalDateTime timestamp) { this.timestamp = timestamp; }

    public java.util.UUID getClientUuid() { return clientUuid; }
    public void setClientUuid(java.util.UUID clientUuid) { this.clientUuid = clientUuid; }
}
