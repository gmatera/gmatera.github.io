package it.prova.receipts;

import it.prova.receipts.account.AccountClient;
import it.prova.receipts.closure.TransmissionClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.kafka.KafkaContainer;

/**
 * Base per i test di integrazione: MySQL e Kafka veri (Testcontainers).
 * Account e il servizio esterno di trasmissione sono sostituiti da mock.
 */
@SpringBootTest(properties = {
        "transmission.job.initial-delay-ms=3600000", // nei test il job lo lancio io a mano
        "transmission.retry.base-delay-seconds=0",
        "transmission.retry.max-attempts=3"
})
public abstract class IntegrationTestBase {

    @ServiceConnection
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4");

    @ServiceConnection
    static final KafkaContainer KAFKA = new KafkaContainer("apache/kafka:3.9.1");

    static {
        MYSQL.start();
        KAFKA.start();
    }

    @MockitoBean
    protected AccountClient accountClient;

    @MockitoBean
    protected TransmissionClient transmissionClient;
}
