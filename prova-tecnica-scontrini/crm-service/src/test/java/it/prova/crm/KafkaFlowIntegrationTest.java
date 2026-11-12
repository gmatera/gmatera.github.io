package it.prova.crm;

import it.prova.crm.config.KafkaConfig;
import it.prova.crm.enablement.CrmEnablementService;
import it.prova.crm.purchase.PurchaseService;
import it.prova.crm.purchase.PurchaseService.CustomerSummary;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.core.KafkaTemplate;

import java.time.Duration;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * Test end-to-end del CRM: pubblico su Kafka gli stessi JSON che pubblicano Account e Receipts
 * e controllo che lo storico acquisti venga aggiornato.
 */
class KafkaFlowIntegrationTest extends IntegrationTestBase {

    @Autowired
    private KafkaTemplate<String, String> kafkaTemplate;

    @Autowired
    private CrmEnablementService enablementService;

    @Autowired
    private PurchaseService purchaseService;

    @Test
    void receiptEventsFromKafkaUpdateCustomerHistory() {
        String org = UUID.randomUUID().toString();

        kafkaTemplate.send(KafkaConfig.ENABLEMENT_TOPIC, org, """
                {"eventId":"e1","eventType":"ENABLEMENT_CHANGED","organizationId":"%s","product":"CRM",
                 "status":"ENABLED","startDate":"2026-01-01","endDate":null,"occurredAt":"2026-01-01T08:00:00Z"}
                """.formatted(org));

        await().atMost(Duration.ofSeconds(30))
                .until(() -> enablementService.isEnabled(org, LocalDate.of(2026, 3, 10)));

        String issued = """
                {"eventId":"r1","eventType":"RECEIPT_ISSUED","receiptId":100,"originalReceiptId":null,
                 "organizationId":"%s","cashRegisterId":"CASSA-1","number":1,"customerId":"anna",
                 "totalAmount":25.00,"issuedAt":"2026-03-10T10:00:00Z"}
                """.formatted(org);

        // lo stesso evento arriva tre volte (es. ripubblicato dalla outbox): deve contare una volta sola
        kafkaTemplate.send(KafkaConfig.RECEIPT_TOPIC, org, issued);
        kafkaTemplate.send(KafkaConfig.RECEIPT_TOPIC, org, issued);
        kafkaTemplate.send(KafkaConfig.RECEIPT_TOPIC, org, issued);

        await().atMost(Duration.ofSeconds(30))
                .until(() -> purchaseService.summary(org, "anna").purchaseCount() > 0);

        await().during(Duration.ofSeconds(2)).atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
            CustomerSummary summary = purchaseService.summary(org, "anna");
            assertThat(summary.purchaseCount()).isEqualTo(1);
            assertThat(summary.totalSpent()).isEqualByComparingTo("25.00");
        });
    }
}
