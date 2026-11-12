package it.prova.stub;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Finto ente esterno: a volte risponde con errore, a volte risponde in ritardo.
 * Come farebbe un ente vero, è idempotente sul closureId: se riceve due volte la stessa
 * chiusura restituisce lo stesso protocollo invece di registrarla due volte.
 */
@RestController
public class TransmissionController {

    private static final Logger log = LoggerFactory.getLogger(TransmissionController.class);

    private final Map<Long, Received> received = new ConcurrentHashMap<>();

    private final double failureRate;
    private final double slowRate;
    private final long slowDelayMs;

    public TransmissionController(@Value("${stub.failure-rate}") double failureRate,
                                  @Value("${stub.slow-rate}") double slowRate,
                                  @Value("${stub.slow-delay-ms}") long slowDelayMs) {
        this.failureRate = failureRate;
        this.slowRate = slowRate;
        this.slowDelayMs = slowDelayMs;
    }

    @PostMapping("/transmissions")
    public ResponseEntity<Map<String, String>> receive(@RequestBody TransmissionRequest request) throws InterruptedException {
        double dice = ThreadLocalRandom.current().nextDouble();
        if (dice < failureRate) {
            log.warn("Chiusura {}: simulo un errore", request.closureId());
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(Map.of("error", "servizio non disponibile"));
        }
        if (dice < failureRate + slowRate) {
            log.warn("Chiusura {}: simulo una risposta lenta", request.closureId());
            Thread.sleep(slowDelayMs);
        }

        Received r = received.compute(request.closureId(), (id, existing) -> existing == null
                ? new Received("PROT-" + UUID.randomUUID().toString().substring(0, 8), 1)
                : new Received(existing.protocolNumber(), existing.timesReceived() + 1));
        log.info("Chiusura {} ricevuta (volte: {}), protocollo {}", request.closureId(), r.timesReceived(), r.protocolNumber());
        return ResponseEntity.ok(Map.of("protocolNumber", r.protocolNumber()));
    }

    /** utile per controllare a mano cosa è arrivato e quante volte */
    @GetMapping("/transmissions")
    public Map<Long, Received> list() {
        return received;
    }

    public record TransmissionRequest(Long closureId) {
    }

    public record Received(String protocolNumber, int timesReceived) {
    }
}
