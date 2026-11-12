package it.prova.common;

import java.time.Instant;
import java.util.List;

/**
 * Formato di errore uguale per tutte le API di tutti i servizi.
 */
public record ErrorResponse(
        Instant timestamp,
        int status,
        String code,
        String message,
        String path,
        String correlationId,
        List<String> details
) {
}
