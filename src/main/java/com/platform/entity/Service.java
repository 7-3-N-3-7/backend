package com.platform.entity;

import com.platform.core.entity.BaseEntity;
import jakarta.persistence.*;
import java.util.UUID;

@Entity
@Table(name = "services")
public class Service extends BaseEntity<UUID> {

    @ManyToOne
    @JoinColumn(name = "provider_profile_id", nullable = true)
    private ProviderProfile provider;

    @ManyToOne
    @JoinColumn(name = "organisation_id", nullable = true)
    private Organisation organisation;

    private String name;
    
    @Column(columnDefinition = "TEXT")
    private String description;
    
    private Integer durationMinutes;
    private Double price;
    private String location;

    @Column(name = "is_active")
    private Boolean isActive = true;

    public Service() {}

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public ProviderProfile getProvider() { return provider; }
    public void setProvider(ProviderProfile provider) { this.provider = provider; }

    public Organisation getOrganisation() { return organisation; }
    public void setOrganisation(Organisation organisation) { this.organisation = organisation; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public Integer getDurationMinutes() { return durationMinutes; }
    public void setDurationMinutes(Integer durationMinutes) { this.durationMinutes = durationMinutes; }

    public Double getPrice() { return price; }
    public void setPrice(Double price) { this.price = price; }

    public String getLocation() { return location; }
    public void setLocation(String location) { this.location = location; }

    public Boolean getIsActive() { return isActive != null ? isActive : true; }
    public void setIsActive(Boolean isActive) { this.isActive = isActive; }
}
