package it.prova.account.enablement;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Evento pubblicato su Kafka a ogni modifica di un'abilitazione.
 * Contiene lo stato completo, così chi lo consuma non deve richiamare Account.
 */
public record EnablementChangedEvent(
        String eventId,
        String eventType,
        String organizationId,
        Product product,
        EnablementStatus status,
        LocalDate startDate,
        LocalDate endDate,
        Instant occurredAt
) {
    public static final String TYPE = "ENABLEMENT_CHANGED";

    public static EnablementChangedEvent from(Enablement enablement) {
        return new EnablementChangedEvent(
                UUID.randomUUID().toString(),
                TYPE,
                enablement.getOrganizationId(),
                enablement.getProduct(),
                enablement.getStatus(),
                enablement.getStartDate(),
                enablement.getEndDate(),
                enablement.getUpdatedAt());
    }
}
