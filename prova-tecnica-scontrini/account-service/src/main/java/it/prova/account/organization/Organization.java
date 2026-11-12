package it.prova.account.organization;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "organization")
public class Organization {

    @Id
    private String id;

    private String name;

    @Enumerated(EnumType.STRING)
    private OrganizationStatus status;

    private Instant createdAt;

    protected Organization() {
        // per JPA
    }

    public Organization(String name, Instant createdAt) {
        this.id = UUID.randomUUID().toString();
        this.name = name;
        this.status = OrganizationStatus.ACTIVE;
        this.createdAt = createdAt;
    }

    public void changeStatus(OrganizationStatus status) {
        this.status = status;
    }

    public boolean isActive() {
        return status == OrganizationStatus.ACTIVE;
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public OrganizationStatus getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
