package it.prova.crm.enablement;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.time.LocalDate;

/**
 * Copia locale dell'abilitazione al prodotto CRM, costruita dagli eventi di Account.
 */
@Document("crm_enablements")
public class CrmEnablement {

    @Id
    private String organizationId;

    private String status;

    private LocalDate startDate;

    private LocalDate endDate;

    /** occurredAt dell'ultimo evento applicato, serve a scartare gli eventi vecchi */
    private Instant lastEventAt;

    public CrmEnablement() {
    }

    public CrmEnablement(String organizationId, String status, LocalDate startDate, LocalDate endDate, Instant lastEventAt) {
        this.organizationId = organizationId;
        this.status = status;
        this.startDate = startDate;
        this.endDate = endDate;
        this.lastEventAt = lastEventAt;
    }

    public boolean isActiveOn(LocalDate date) {
        return "ENABLED".equals(status)
                && !date.isBefore(startDate)
                && (endDate == null || !date.isAfter(endDate));
    }

    public String getOrganizationId() {
        return organizationId;
    }

    public String getStatus() {
        return status;
    }

    public LocalDate getStartDate() {
        return startDate;
    }

    public LocalDate getEndDate() {
        return endDate;
    }

    public Instant getLastEventAt() {
        return lastEventAt;
    }
}
