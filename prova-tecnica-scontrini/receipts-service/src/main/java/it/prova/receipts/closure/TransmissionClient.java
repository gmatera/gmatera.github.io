package it.prova.receipts.closure;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Client verso il servizio esterno di trasmissione (simulato da transmission-stub).
 * Qualsiasi errore (5xx, timeout, connessione rifiutata) arriva al chiamante come eccezione.
 */
@Component
public class TransmissionClient {

    private final RestClient restClient;

    public TransmissionClient(@Qualifier("transmissionRestClient") RestClient restClient) {
        this.restClient = restClient;
    }

    public String send(TransmissionRequest request) {
        TransmissionResponse response = restClient.post()
                .uri("/transmissions")
                .body(request)
                .retrieve()
                .body(TransmissionResponse.class);
        if (response == null || response.protocolNumber() == null) {
            throw new IllegalStateException("Risposta senza numero di protocollo");
        }
        return response.protocolNumber();
    }

    /**
     * closureId fa anche da chiave di idempotenza per l'ente: se lo stesso invio arriva due volte
     * (es. timeout lato nostro ma ricevuto da loro) l'ente restituisce lo stesso protocollo.
     */
    public record TransmissionRequest(Long closureId, String organizationId, String cashRegisterId,
                                      LocalDate businessDate, int receiptCount, BigDecimal totalAmount,
                                      List<Document> documents) {
    }

    public record Document(int number, String type, BigDecimal totalAmount, BigDecimal vatAmount) {
    }

    public record TransmissionResponse(String protocolNumber) {
    }
}
