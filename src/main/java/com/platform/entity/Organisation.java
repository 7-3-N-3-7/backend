package com.platform.entity;

import com.platform.core.entity.BaseEntity;
import jakarta.persistence.*;
import java.util.UUID;

@Entity
@Table(name = "organisations")
public class Organisation extends BaseEntity<UUID> {

    @Column(name = "cvr_number", unique = true, nullable = false, length = 20)
    private String cvrNumber;

    @Column(nullable = false)
    private String name;

    @Column(name = "address")
    private String address;

    @Column(name = "postal_code")
    private String postalCode;

    @Column(name = "phone_number")
    private String phoneNumber;

    @Column(name = "email")
    private String email;

    @Column(name = "video_meeting_url", length = 500)
    private String videoMeetingUrl;

    public Organisation() {}

    public String getVideoMeetingUrl() { return videoMeetingUrl; }
    public void setVideoMeetingUrl(String videoMeetingUrl) { this.videoMeetingUrl = videoMeetingUrl; }

    public Organisation(String cvrNumber, String name) {
        this.cvrNumber = cvrNumber;
        this.name = name;
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public String getCvrNumber() { return cvrNumber; }
    public void setCvrNumber(String cvrNumber) { this.cvrNumber = cvrNumber; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getAddress() { return address; }
    public void setAddress(String address) { this.address = address; }

    public String getPostalCode() { return postalCode; }
    public void setPostalCode(String postalCode) { this.postalCode = postalCode; }

    public String getPhoneNumber() { return phoneNumber; }
    public void setPhoneNumber(String phoneNumber) { this.phoneNumber = phoneNumber; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
}
