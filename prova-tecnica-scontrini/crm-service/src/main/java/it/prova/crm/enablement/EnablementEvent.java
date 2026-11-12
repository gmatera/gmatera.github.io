package it.prova.crm.enablement;

import java.time.Instant;
import java.time.LocalDate;

/**
 * Evento di Account come lo vede il CRM (solo i campi che mi servono).
 */
public record EnablementEvent(
        String eventId,
        String organizationId,
        String product,
        String status,
        LocalDate startDate,
        LocalDate endDate,
        Instant occurredAt
) {
}
