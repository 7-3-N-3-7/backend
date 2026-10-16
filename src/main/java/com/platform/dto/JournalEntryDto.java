package com.platform.dto;

<<<<<<< HEAD
=======
<<<<<<< HEAD
<<<<<<< HEAD
<<<<<<< HEAD
=======
>>>>>>> 16f2191da8c19882528cd7f0f6ff79480a370715
>>>>>>> d6bd62f49cdd26c1f0dfc9614c1ba49cc9f83309
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

<<<<<<< HEAD
=======
=======
<<<<<<< HEAD
=======
>>>>>>> 266b19092e0a4e40a20f5cd2eaf04be642298e92
=======
>>>>>>> 16f2191da8c19882528cd7f0f6ff79480a370715
import java.util.UUID;

public class JournalEntryDto {
    private String title;
    private String content;
    private Integer moodRating;

    public JournalEntryDto() {}

<<<<<<< HEAD
<<<<<<< HEAD
>>>>>>> 3d3aa53f18dac79b5509f233401d317d3db0787d
=======
>>>>>>> 266b19092e0a4e40a20f5cd2eaf04be642298e92
=======
>>>>>>> 3d3aa53f18dac79b5509f233401d317d3db0787d
>>>>>>> 16f2191da8c19882528cd7f0f6ff79480a370715
>>>>>>> d6bd62f49cdd26c1f0dfc9614c1ba49cc9f83309
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    
    public Integer getMoodRating() { return moodRating; }
    public void setMoodRating(Integer moodRating) { this.moodRating = moodRating; }
<<<<<<< HEAD
=======
<<<<<<< HEAD
<<<<<<< HEAD
<<<<<<< HEAD
=======
>>>>>>> 16f2191da8c19882528cd7f0f6ff79480a370715
>>>>>>> d6bd62f49cdd26c1f0dfc9614c1ba49cc9f83309

    public Boolean getSharedWithTherapists() { return sharedWithTherapists != null ? sharedWithTherapists : true; }
    public void setSharedWithTherapists(Boolean sharedWithTherapists) { this.sharedWithTherapists = sharedWithTherapists; }

    public List<UUID> getSharedTherapistUuids() { return sharedTherapistUuids; }
    public void setSharedTherapistUuids(List<UUID> sharedTherapistUuids) { this.sharedTherapistUuids = sharedTherapistUuids; }

    public UUID getClientUuid() { return clientUuid; }
    public void setClientUuid(UUID clientUuid) { this.clientUuid = clientUuid; }

    public UUID getAppointmentId() { return appointmentId; }
    public void setAppointmentId(UUID appointmentId) { this.appointmentId = appointmentId; }
<<<<<<< HEAD
=======
=======
>>>>>>> 3d3aa53f18dac79b5509f233401d317d3db0787d
<<<<<<< HEAD
=======
>>>>>>> 266b19092e0a4e40a20f5cd2eaf04be642298e92
=======
>>>>>>> 16f2191da8c19882528cd7f0f6ff79480a370715
>>>>>>> d6bd62f49cdd26c1f0dfc9614c1ba49cc9f83309
}
