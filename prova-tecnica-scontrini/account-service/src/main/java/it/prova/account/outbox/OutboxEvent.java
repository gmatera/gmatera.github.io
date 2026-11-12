package it.prova.account.outbox;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "outbox_event")
public class OutboxEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String topic;

    private String messageKey;

    private String eventType;

    private String payload;

    private String correlationId;

    private Instant createdAt;

    private Instant publishedAt;

    protected OutboxEvent() {
        // per JPA
    }

    public OutboxEvent(String topic, String messageKey, String eventType, String payload,
                       String correlationId, Instant createdAt) {
        this.topic = topic;
        this.messageKey = messageKey;
        this.eventType = eventType;
        this.payload = payload;
        this.correlationId = correlationId;
        this.createdAt = createdAt;
    }

    public void markPublished(Instant when) {
        this.publishedAt = when;
    }

    public Long getId() {
        return id;
    }

    public String getTopic() {
        return topic;
    }

    public String getMessageKey() {
        return messageKey;
    }

    public String getEventType() {
        return eventType;
    }

    public String getPayload() {
        return payload;
    }

    public String getCorrelationId() {
        return correlationId;
    }

    public Instant getPublishedAt() {
        return publishedAt;
    }
}
