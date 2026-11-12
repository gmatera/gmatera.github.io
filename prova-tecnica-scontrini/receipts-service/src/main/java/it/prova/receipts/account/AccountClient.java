package it.prova.receipts.account;

import it.prova.common.ApiException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Controllo sincrono dell'abilitazione verso il servizio Account.
 * Se Account non risponde il documento NON viene emesso (errore 503): preferisco rifiutare
 * un'emissione piuttosto che emettere per un'organizzazione sospesa o non abilitata.
 */
@Component
public class AccountClient {

    private static final Logger log = LoggerFactory.getLogger(AccountClient.class);
    private static final String PRODUCT = "SCONTRINA";

    private final RestClient restClient;

    public AccountClient(@Qualifier("accountRestClient") RestClient restClient) {
        this.restClient = restClient;
    }

    public void checkCanIssue(String organizationId) {
        AccessResponse access;
        try {
            access = restClient.get()
                    .uri("/organizations/{id}/access/{product}", organizationId, PRODUCT)
                    .retrieve()
                    .body(AccessResponse.class);
        } catch (HttpClientErrorException.NotFound e) {
            throw ApiException.notFound("ORGANIZATION_NOT_FOUND", "Organizzazione " + organizationId + " non trovata");
        } catch (RestClientException e) {
            log.warn("Servizio Account non raggiungibile: {}", e.getMessage());
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "ACCOUNT_UNAVAILABLE",
                    "Impossibile verificare l'abilitazione dell'organizzazione, riprovare più tardi");
        }

        if (access == null || !access.allowed()) {
            String reason = access != null ? access.reason() : "UNKNOWN";
            throw new ApiException(HttpStatus.FORBIDDEN, reason,
                    "L'organizzazione " + organizationId + " non può emettere documenti (" + reason + ")");
        }
    }

    public record AccessResponse(String organizationId, String product, boolean allowed, String reason) {
    }
}
