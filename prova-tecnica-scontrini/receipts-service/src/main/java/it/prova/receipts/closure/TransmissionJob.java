package it.prova.receipts.closure;

import it.prova.common.CorrelationId;
import it.prova.receipts.closure.TransmissionClient.Document;
import it.prova.receipts.closure.TransmissionClient.TransmissionRequest;
import it.prova.receipts.receipt.ReceiptRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

/**
 * Job che trasmette all'ente esterno le chiusure in stato TO_SEND, con retry e backoff.
 */
@Component
public class TransmissionJob {

    private static final Logger log = LoggerFactory.getLogger(TransmissionJob.class);

    private final DailyClosureRepository closureRepository;
    private final ReceiptRepository receiptRepository;
    private final TransmissionClient transmissionClient;
    private final RetryPolicy retryPolicy;
    private final Clock clock;

    public TransmissionJob(DailyClosureRepository closureRepository, ReceiptRepository receiptRepository,
                           TransmissionClient transmissionClient, RetryPolicy retryPolicy, Clock clock) {
        this.closureRepository = closureRepository;
        this.receiptRepository = receiptRepository;
        this.transmissionClient = transmissionClient;
        this.retryPolicy = retryPolicy;
        this.clock = clock;
    }

    @Scheduled(initialDelayString = "${transmission.job.initial-delay-ms}", fixedDelayString = "${transmission.job.delay-ms}")
    public void run() {
        List<DailyClosure> ready = closureRepository
                .findByStatusAndNextAttemptAtLessThanEqualOrderByNextAttemptAt(ClosureStatus.TO_SEND, Instant.now(clock));
        for (DailyClosure closure : ready) {
            transmit(closure.getId());
        }
    }

    void transmit(Long closureId) {
        // solo chi riesce a passare la chiusura da TO_SEND a IN_PROGRESS la invia
        if (closureRepository.claim(closureId, Instant.now(clock)) == 0) {
            log.debug("Chiusura {} già presa da un'altra esecuzione", closureId);
            return;
        }

        CorrelationId.set(CorrelationId.newId());
        DailyClosure closure = closureRepository.findById(closureId).orElseThrow();
        try {
            String protocol = transmissionClient.send(buildRequest(closure));
            closure.markSent(protocol, Instant.now(clock));
            log.info("Chiusura {} trasmessa, protocollo {}", closureId, protocol);
        } catch (Exception e) {
            handleFailure(closure, e);
        } finally {
            closureRepository.save(closure);
            CorrelationId.clear();
        }
    }

    private void handleFailure(DailyClosure closure, Exception e) {
        Instant now = Instant.now(clock);
        String error = e.getClass().getSimpleName() + ": " + e.getMessage();
        if (retryPolicy.canRetry(closure.getAttempts())) {
            Instant next = now.plus(retryPolicy.delayAfter(closure.getAttempts()));
            closure.retryLater(error, next, now);
            log.warn("Trasmissione chiusura {} fallita (tentativo {}), riprovo alle {}: {}",
                    closure.getId(), closure.getAttempts(), next, error);
        } else {
            closure.markFailed(error, now);
            log.error("Trasmissione chiusura {} fallita definitivamente dopo {} tentativi: {}",
                    closure.getId(), closure.getAttempts(), error);
        }
    }

    private TransmissionRequest buildRequest(DailyClosure closure) {
        List<Document> documents = receiptRepository
                .findByOrganizationIdAndCashRegisterIdAndBusinessDateOrderByNumber(
                        closure.getOrganizationId(), closure.getCashRegisterId(), closure.getBusinessDate())
                .stream()
                .map(r -> new Document(r.getNumber(), r.getType().name(), r.getTotalAmount(), r.getVatAmount()))
                .toList();
        return new TransmissionRequest(closure.getId(), closure.getOrganizationId(), closure.getCashRegisterId(),
                closure.getBusinessDate(), closure.getReceiptCount(), closure.getTotalAmount(), documents);
    }
}
