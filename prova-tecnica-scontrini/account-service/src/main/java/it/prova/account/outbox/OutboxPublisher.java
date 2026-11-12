package it.prova.account.outbox;

import it.prova.common.CorrelationId;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Legge periodicamente la tabella outbox e pubblica su Kafka gli eventi non ancora inviati.
 * Garanzia "at least once": in caso di crash dopo l'invio ma prima del commit un evento può
 * essere ripubblicato, per questo i consumer devono essere idempotenti.
 */
@Component
public class OutboxPublisher {

    private static final Logger log = LoggerFactory.getLogger(OutboxPublisher.class);

    private final OutboxRepository repository;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final Clock clock;

    public OutboxPublisher(OutboxRepository repository, KafkaTemplate<String, String> kafkaTemplate, Clock clock) {
        this.repository = repository;
        this.kafkaTemplate = kafkaTemplate;
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "${outbox.publish-delay-ms}")
    @Transactional
    public void publishPending() {
        List<OutboxEvent> batch = repository.lockNextBatch();
        for (OutboxEvent event : batch) {
            try {
                // aspetto la conferma del broker prima di segnare l'evento come pubblicato
                kafkaTemplate.send(toRecord(event)).get(10, TimeUnit.SECONDS);
                event.markPublished(Instant.now(clock));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            } catch (Exception e) {
                // mi fermo qui per non cambiare l'ordine degli eventi: riprovo al prossimo giro
                log.warn("Pubblicazione dell'evento {} fallita, riprovo più tardi", event.getId(), e);
                return;
            }
        }
    }

    private ProducerRecord<String, String> toRecord(OutboxEvent event) {
        ProducerRecord<String, String> record =
                new ProducerRecord<>(event.getTopic(), event.getMessageKey(), event.getPayload());
        if (event.getCorrelationId() != null) {
            record.headers().add(CorrelationId.HEADER, event.getCorrelationId().getBytes(StandardCharsets.UTF_8));
        }
        return record;
    }
}
