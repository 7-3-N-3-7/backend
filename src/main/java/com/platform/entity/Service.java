package com.platform.entity;

<<<<<<< HEAD
import com.platform.core.entity.BaseEntity;
=======
>>>>>>> 3d3aa53f18dac79b5509f233401d317d3db0787d
import jakarta.persistence.*;
import java.util.UUID;

@Entity
@Table(name = "services")
<<<<<<< HEAD
public class Service extends BaseEntity<UUID> {

    @ManyToOne
    @JoinColumn(name = "provider_profile_id", nullable = true)
    private ProviderProfile provider;

    @ManyToOne
    @JoinColumn(name = "organisation_id", nullable = true)
    private Organisation organisation;

=======
public class Service {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne
    @JoinColumn(name = "provider_profile_id", nullable = false)
    private ProviderProfile provider;

>>>>>>> 3d3aa53f18dac79b5509f233401d317d3db0787d
    private String name;
    
    @Column(columnDefinition = "TEXT")
    private String description;
    
    private Integer durationMinutes;
    private Double price;
    private String location;

<<<<<<< HEAD
    @Column(name = "is_active")
    private Boolean isActive = true;

=======
>>>>>>> 3d3aa53f18dac79b5509f233401d317d3db0787d
    public Service() {}

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public ProviderProfile getProvider() { return provider; }
    public void setProvider(ProviderProfile provider) { this.provider = provider; }

<<<<<<< HEAD
    public Organisation getOrganisation() { return organisation; }
    public void setOrganisation(Organisation organisation) { this.organisation = organisation; }

=======
>>>>>>> 3d3aa53f18dac79b5509f233401d317d3db0787d
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
<<<<<<< HEAD

    public Boolean getIsActive() { return isActive != null ? isActive : true; }
    public void setIsActive(Boolean isActive) { this.isActive = isActive; }
=======
>>>>>>> 3d3aa53f18dac79b5509f233401d317d3db0787d
}
