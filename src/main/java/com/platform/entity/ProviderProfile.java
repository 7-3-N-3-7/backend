package com.platform.entity;

import jakarta.persistence.*;
import java.util.UUID;

@Entity
@Table(name = "provider_profiles")
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

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public UserEntity getUser() { return user; }
    public void setUser(UserEntity user) { this.user = user; }

    public String getBiography() { return biography; }
    public void setBiography(String biography) { this.biography = biography; }

    public String getWorkingHoursJson() { return workingHoursJson; }
    public void setWorkingHoursJson(String workingHoursJson) { this.workingHoursJson = workingHoursJson; }
}
