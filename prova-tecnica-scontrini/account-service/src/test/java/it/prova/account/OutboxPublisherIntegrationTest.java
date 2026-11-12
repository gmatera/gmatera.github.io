package it.prova.account;

import it.prova.account.config.AppConfig;
import it.prova.account.enablement.EnablementService;
import it.prova.account.enablement.EnablementStatus;
import it.prova.account.enablement.Product;
import it.prova.account.organization.Organization;
import it.prova.account.organization.OrganizationService;
import it.prova.common.CorrelationId;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

class OutboxPublisherIntegrationTest extends IntegrationTestBase {

    @Autowired
    private OrganizationService organizationService;

    @Autowired
    private EnablementService enablementService;

    @Test
    void enablementChangeIsPublishedOnKafkaWithCorrelationId() {
        Organization org = organizationService.create("Gelateria Blu");

        CorrelationId.set("test-correlation-id");
        try {
            enablementService.provision(org.getId(), Product.CRM, EnablementStatus.ENABLED, LocalDate.of(2026, 1, 1), null);
        } finally {
            CorrelationId.clear();
        }

        try (KafkaConsumer<String, String> consumer = newConsumer()) {
            consumer.subscribe(List.of(AppConfig.ENABLEMENT_TOPIC));
            List<ConsumerRecord<String, String>> received = new ArrayList<>();

            await().atMost(Duration.ofSeconds(30)).until(() -> {
                consumer.poll(Duration.ofMillis(500)).forEach(received::add);
                return received.stream().anyMatch(r -> org.getId().equals(r.key()));
            });

            ConsumerRecord<String, String> record = received.stream()
                    .filter(r -> org.getId().equals(r.key())).findFirst().orElseThrow();
            assertThat(record.value()).contains("\"product\":\"CRM\"").contains("\"status\":\"ENABLED\"");
            assertThat(new String(record.headers().lastHeader(CorrelationId.HEADER).value(), StandardCharsets.UTF_8))
                    .isEqualTo("test-correlation-id");
        }
    }

    private KafkaConsumer<String, String> newConsumer() {
        return new KafkaConsumer<>(Map.of(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers(),
                ConsumerConfig.GROUP_ID_CONFIG, "test-" + UUID.randomUUID(),
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest",
                ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class,
                ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class));
    }
}
