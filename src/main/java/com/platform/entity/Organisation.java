package com.platform.entity;

import jakarta.persistence.*;
import java.util.UUID;

@Entity
@Table(name = "organisations")
public class Organisation {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "cvr_number", unique = true, nullable = false, length = 20)
    private String cvrNumber;

    @Column(nullable = false)
    private String name;

    public Organisation() {}

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
}
