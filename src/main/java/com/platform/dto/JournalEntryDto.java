package com.platform.dto;

import com.platform.core.dto.BaseDTO;
import java.util.List;
import java.util.UUID;

public class JournalEntryDto implements BaseDTO<UUID> {
    private UUID id;
    private String title;
    private String content;
    private Integer moodRating;
    private Boolean sharedWithTherapists = true;
    private List<UUID> sharedTherapistUuids;
    private UUID clientUuid;
    private UUID appointmentId;

    public JournalEntryDto() {}

    @Override
    public UUID getId() { return id; }

    @Override
    public void setId(UUID id) { this.id = id; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    
    public Integer getMoodRating() { return moodRating; }
    public void setMoodRating(Integer moodRating) { this.moodRating = moodRating; }

    public Boolean getSharedWithTherapists() { return sharedWithTherapists != null ? sharedWithTherapists : true; }
    public void setSharedWithTherapists(Boolean sharedWithTherapists) { this.sharedWithTherapists = sharedWithTherapists; }

    public List<UUID> getSharedTherapistUuids() { return sharedTherapistUuids; }
    public void setSharedTherapistUuids(List<UUID> sharedTherapistUuids) { this.sharedTherapistUuids = sharedTherapistUuids; }

    public UUID getClientUuid() { return clientUuid; }
    public void setClientUuid(UUID clientUuid) { this.clientUuid = clientUuid; }

    public UUID getAppointmentId() { return appointmentId; }
    public void setAppointmentId(UUID appointmentId) { this.appointmentId = appointmentId; }
}
