package com.platform.entity;

<<<<<<< HEAD
<<<<<<< HEAD
import com.platform.core.entity.BaseEntity;
=======
>>>>>>> 3d3aa53f18dac79b5509f233401d317d3db0787d
=======
>>>>>>> 266b19092e0a4e40a20f5cd2eaf04be642298e92
import jakarta.persistence.*;
import java.util.UUID;

@Entity
@Table(name = "provider_profiles")
<<<<<<< HEAD
<<<<<<< HEAD
public class ProviderProfile extends BaseEntity<UUID> {

    @OneToOne
    @JoinColumn(name = "user_id", referencedColumnName = "id", nullable = true)
    private UserEntity user;

    @Column(name = "user_uuid")
    private UUID userUuid;

    @Column(name = "display_name")
    private String displayName;

    @ManyToOne
    @JoinColumn(name = "organisation_id", nullable = true)
    private Organisation organisation;

    @Column(columnDefinition = "TEXT")
    private String biography;

    @Column(name = "working_hours_json", columnDefinition = "TEXT")
    private String workingHoursJson;

    @Column(name = "video_meeting_url", length = 500)
    private String videoMeetingUrl;

    public ProviderProfile() {}

    public String getVideoMeetingUrl() { return videoMeetingUrl; }
    public void setVideoMeetingUrl(String videoMeetingUrl) { this.videoMeetingUrl = videoMeetingUrl; }

=======
=======
>>>>>>> 266b19092e0a4e40a20f5cd2eaf04be642298e92
public class ProviderProfile {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @OneToOne
    @JoinColumn(name = "user_id", referencedColumnName = "id", nullable = false)
    private UserEntity user;

    @Column(columnDefinition = "TEXT")
    private String biography;

    @Column(name = "working_hours_json")
    private String workingHoursJson;

    public ProviderProfile() {}

<<<<<<< HEAD
>>>>>>> 3d3aa53f18dac79b5509f233401d317d3db0787d
=======
>>>>>>> 266b19092e0a4e40a20f5cd2eaf04be642298e92
    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public UserEntity getUser() { return user; }
    public void setUser(UserEntity user) { this.user = user; }

<<<<<<< HEAD
<<<<<<< HEAD
    public UUID getUserUuid() { return userUuid; }
    public void setUserUuid(UUID userUuid) { this.userUuid = userUuid; }

    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }

    public Organisation getOrganisation() { return organisation; }
    public void setOrganisation(Organisation organisation) { this.organisation = organisation; }

=======
>>>>>>> 3d3aa53f18dac79b5509f233401d317d3db0787d
=======
>>>>>>> 266b19092e0a4e40a20f5cd2eaf04be642298e92
    public String getBiography() { return biography; }
    public void setBiography(String biography) { this.biography = biography; }

    public String getWorkingHoursJson() { return workingHoursJson; }
    public void setWorkingHoursJson(String workingHoursJson) { this.workingHoursJson = workingHoursJson; }
}
