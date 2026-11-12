package it.prova.crm;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import it.prova.common.CorrelationId;
import it.prova.crm.config.KafkaConfig;
import it.prova.crm.enablement.CrmEnablementService;
import it.prova.crm.enablement.EnablementEvent;
import it.prova.crm.purchase.PurchaseService;
import it.prova.crm.purchase.ReceiptEvent;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Header;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

/**
 * Consumer Kafka del CRM. Legge il JSON, imposta il correlation id ricevuto nell'header
 * (così i log del CRM si collegano a quelli del servizio che ha generato l'evento) e delega ai service.
 */
@Component
public class EventListeners {

    private static final Logger log = LoggerFactory.getLogger(EventListeners.class);

    private final ObjectMapper objectMapper;
    private final CrmEnablementService enablementService;
    private final PurchaseService purchaseService;

    public EventListeners(ObjectMapper objectMapper, CrmEnablementService enablementService, PurchaseService purchaseService) {
        this.objectMapper = objectMapper;
        this.enablementService = enablementService;
        this.purchaseService = purchaseService;
    }

    @KafkaListener(topics = KafkaConfig.ENABLEMENT_TOPIC)
    public void onEnablementEvent(ConsumerRecord<String, String> record) throws JsonProcessingException {
        setCorrelationId(record);
        try {
            enablementService.onEnablementChanged(objectMapper.readValue(record.value(), EnablementEvent.class));
        } finally {
            CorrelationId.clear();
        }
    }

    @KafkaListener(topics = KafkaConfig.RECEIPT_TOPIC)
    public void onReceiptEvent(ConsumerRecord<String, String> record) throws JsonProcessingException {
        setCorrelationId(record);
        try {
            ReceiptEvent event = objectMapper.readValue(record.value(), ReceiptEvent.class);
            switch (event.eventType()) {
                case ReceiptEvent.ISSUED -> purchaseService.onReceiptIssued(event);
                case ReceiptEvent.CANCELLED -> purchaseService.onReceiptCancelled(event);
                default -> log.warn("Tipo di evento sconosciuto: {}", event.eventType());
            }
        } finally {
            CorrelationId.clear();
        }
    }

    private void setCorrelationId(ConsumerRecord<String, String> record) {
        Header header = record.headers().lastHeader(CorrelationId.HEADER);
        String id = header != null ? new String(header.value(), StandardCharsets.UTF_8) : CorrelationId.newId();
        CorrelationId.set(id);
    }
}
