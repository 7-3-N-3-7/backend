package com.platform.entity;

<<<<<<< HEAD
import com.platform.core.entity.BaseEntity;
=======
<<<<<<< HEAD
<<<<<<< HEAD
<<<<<<< HEAD
import com.platform.core.entity.BaseEntity;
=======
>>>>>>> 3d3aa53f18dac79b5509f233401d317d3db0787d
=======
>>>>>>> 266b19092e0a4e40a20f5cd2eaf04be642298e92
=======
import com.platform.core.entity.BaseEntity;
=======
>>>>>>> 3d3aa53f18dac79b5509f233401d317d3db0787d
>>>>>>> 16f2191da8c19882528cd7f0f6ff79480a370715
>>>>>>> d6bd62f49cdd26c1f0dfc9614c1ba49cc9f83309
import jakarta.persistence.*;
import java.util.UUID;

@Entity
@Table(name = "services")
<<<<<<< HEAD
=======
<<<<<<< HEAD
<<<<<<< HEAD
<<<<<<< HEAD
=======
>>>>>>> 16f2191da8c19882528cd7f0f6ff79480a370715
>>>>>>> d6bd62f49cdd26c1f0dfc9614c1ba49cc9f83309
public class Service extends BaseEntity<UUID> {

    @ManyToOne
    @JoinColumn(name = "provider_profile_id", nullable = true)
    private ProviderProfile provider;

    @ManyToOne
    @JoinColumn(name = "organisation_id", nullable = true)
    private Organisation organisation;

<<<<<<< HEAD
=======
=======
<<<<<<< HEAD
=======
>>>>>>> 266b19092e0a4e40a20f5cd2eaf04be642298e92
=======
>>>>>>> 16f2191da8c19882528cd7f0f6ff79480a370715
public class Service {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne
    @JoinColumn(name = "provider_profile_id", nullable = false)
    private ProviderProfile provider;

<<<<<<< HEAD
<<<<<<< HEAD
>>>>>>> 3d3aa53f18dac79b5509f233401d317d3db0787d
=======
>>>>>>> 266b19092e0a4e40a20f5cd2eaf04be642298e92
=======
>>>>>>> 3d3aa53f18dac79b5509f233401d317d3db0787d
>>>>>>> 16f2191da8c19882528cd7f0f6ff79480a370715
>>>>>>> d6bd62f49cdd26c1f0dfc9614c1ba49cc9f83309
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
<<<<<<< HEAD
<<<<<<< HEAD
<<<<<<< HEAD
=======
>>>>>>> 16f2191da8c19882528cd7f0f6ff79480a370715
    @Column(name = "is_active")
    private Boolean isActive = true;

=======
>>>>>>> 3d3aa53f18dac79b5509f233401d317d3db0787d
<<<<<<< HEAD
=======
>>>>>>> 266b19092e0a4e40a20f5cd2eaf04be642298e92
=======
>>>>>>> 16f2191da8c19882528cd7f0f6ff79480a370715
>>>>>>> d6bd62f49cdd26c1f0dfc9614c1ba49cc9f83309
    public Service() {}

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public ProviderProfile getProvider() { return provider; }
    public void setProvider(ProviderProfile provider) { this.provider = provider; }

<<<<<<< HEAD
    public Organisation getOrganisation() { return organisation; }
    public void setOrganisation(Organisation organisation) { this.organisation = organisation; }

=======
<<<<<<< HEAD
<<<<<<< HEAD
<<<<<<< HEAD
=======
>>>>>>> 16f2191da8c19882528cd7f0f6ff79480a370715
    public Organisation getOrganisation() { return organisation; }
    public void setOrganisation(Organisation organisation) { this.organisation = organisation; }

=======
>>>>>>> 3d3aa53f18dac79b5509f233401d317d3db0787d
<<<<<<< HEAD
=======
>>>>>>> 266b19092e0a4e40a20f5cd2eaf04be642298e92
=======
>>>>>>> 16f2191da8c19882528cd7f0f6ff79480a370715
>>>>>>> d6bd62f49cdd26c1f0dfc9614c1ba49cc9f83309
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
<<<<<<< HEAD
<<<<<<< HEAD
<<<<<<< HEAD
=======
>>>>>>> 16f2191da8c19882528cd7f0f6ff79480a370715

    public Boolean getIsActive() { return isActive != null ? isActive : true; }
    public void setIsActive(Boolean isActive) { this.isActive = isActive; }
=======
>>>>>>> 3d3aa53f18dac79b5509f233401d317d3db0787d
<<<<<<< HEAD
=======
>>>>>>> 266b19092e0a4e40a20f5cd2eaf04be642298e92
=======
>>>>>>> 16f2191da8c19882528cd7f0f6ff79480a370715
>>>>>>> d6bd62f49cdd26c1f0dfc9614c1ba49cc9f83309
}
