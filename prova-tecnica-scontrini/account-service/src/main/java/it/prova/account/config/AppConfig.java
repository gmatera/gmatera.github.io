package it.prova.account.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

import java.time.Clock;
import java.time.Duration;
import java.time.ZoneId;

@Configuration
public class AppConfig {

    public static final String ENABLEMENT_TOPIC = "account.enablement-events";

    /**
     * Uso un Clock iniettato invece di LocalDate.now() così nei test posso controllare la data.
     * Precisione al microsecondo, la stessa delle colonne DATETIME(6).
     */
    @Bean
    public Clock clock(@Value("${app.zone}") String zone) {
        return Clock.tick(Clock.system(ZoneId.of(zone)), Duration.ofNanos(1000));
    }

    @Bean
    public NewTopic enablementTopic() {
        return TopicBuilder.name(ENABLEMENT_TOPIC).partitions(3).replicas(1).build();
    }
}
