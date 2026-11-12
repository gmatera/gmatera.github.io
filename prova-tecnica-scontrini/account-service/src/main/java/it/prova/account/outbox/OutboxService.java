package it.prova.account.outbox;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import it.prova.common.CorrelationId;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;

@Service
public class OutboxService {

    private final OutboxRepository repository;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public OutboxService(OutboxRepository repository, ObjectMapper objectMapper, Clock clock) {
        this.repository = repository;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    /**
     * MANDATORY: deve essere chiamato dentro la transazione che modifica i dati,
     * così dato ed evento vengono salvati insieme oppure per niente.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void add(String topic, String key, String eventType, Object event) {
        String payload;
        try {
            payload = objectMapper.writeValueAsString(event);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Impossibile serializzare l'evento " + eventType, e);
        }
        repository.save(new OutboxEvent(topic, key, eventType, payload, CorrelationId.current(), Instant.now(clock)));
    }
}
