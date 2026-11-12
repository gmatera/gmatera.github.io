package it.prova.account.enablement;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;

/**
 * Abilitazione di un'organizzazione a un prodotto, valida tra startDate e endDate (endDate null = senza scadenza).
 */
@Entity
@Table(name = "enablement")
public class Enablement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String organizationId;

    @Enumerated(EnumType.STRING)
    private Product product;

    @Enumerated(EnumType.STRING)
    private EnablementStatus status;

    private LocalDate startDate;

    private LocalDate endDate;

    private Instant updatedAt;

    protected Enablement() {
        // per JPA
    }

    public Enablement(String organizationId, Product product) {
        this.organizationId = organizationId;
        this.product = product;
    }

    public boolean hasSameValues(EnablementStatus status, LocalDate startDate, LocalDate endDate) {
        return this.status == status
                && Objects.equals(this.startDate, startDate)
                && Objects.equals(this.endDate, endDate);
    }

    public void update(EnablementStatus status, LocalDate startDate, LocalDate endDate, Instant now) {
        this.status = status;
        this.startDate = startDate;
        this.endDate = endDate;
        this.updatedAt = now;
    }

    /**
     * Abilitata in una certa data: stato ENABLED e data compresa nel periodo (estremi inclusi).
     */
    public boolean isActiveOn(LocalDate date) {
        return status == EnablementStatus.ENABLED
                && !date.isBefore(startDate)
                && (endDate == null || !date.isAfter(endDate));
    }

    public Long getId() {
        return id;
    }

    public String getOrganizationId() {
        return organizationId;
    }

    public Product getProduct() {
        return product;
    }

    public EnablementStatus getStatus() {
        return status;
    }

    public LocalDate getStartDate() {
        return startDate;
    }

    public LocalDate getEndDate() {
        return endDate;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
