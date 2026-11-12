package it.prova.account;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.kafka.KafkaContainer;

/**
 * Base per i test di integrazione: MySQL e Kafka veri avviati con Testcontainers.
 * I container sono statici e partono una volta sola per tutti i test.
 */
@SpringBootTest
public abstract class IntegrationTestBase {

    @ServiceConnection
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4");

    @ServiceConnection
    static final KafkaContainer KAFKA = new KafkaContainer("apache/kafka:3.9.1");

    static {
        MYSQL.start();
        KAFKA.start();
    }
}
