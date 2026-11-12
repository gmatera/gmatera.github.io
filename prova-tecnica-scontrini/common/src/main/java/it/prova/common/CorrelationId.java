package it.prova.common;

import org.slf4j.MDC;
import org.springframework.http.client.ClientHttpRequestInterceptor;

import java.util.UUID;

/**
 * Gestione dell'identificativo di correlazione.
 * Il valore viene messo nell'MDC di SLF4J, così finisce automaticamente in ogni riga di log JSON.
 */
public final class CorrelationId {

    public static final String HEADER = "X-Correlation-Id";
    public static final String MDC_KEY = "correlationId";

    private CorrelationId() {
    }

    public static String current() {
        return MDC.get(MDC_KEY);
    }

    public static String newId() {
        return UUID.randomUUID().toString();
    }

    public static void set(String id) {
        MDC.put(MDC_KEY, id);
    }

    public static void clear() {
        MDC.remove(MDC_KEY);
    }

    /**
     * Interceptor per RestClient: aggiunge l'header alle chiamate verso gli altri servizi.
     */
    public static ClientHttpRequestInterceptor propagatingInterceptor() {
        return (request, body, execution) -> {
            String id = current();
            if (id != null) {
                request.getHeaders().set(HEADER, id);
            }
            return execution.execute(request, body);
        };
    }
}
