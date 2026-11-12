package it.prova.receipts.config;

import it.prova.common.CorrelationId;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.web.client.RestClient;

import java.time.Clock;
import java.time.Duration;
import java.time.ZoneId;

@Configuration
public class AppConfig {

    public static final String RECEIPT_TOPIC = "receipts.receipt-events";

    /**
     * Clock iniettato (testabile), con precisione al microsecondo come le colonne DATETIME(6).
     */
    @Bean
    public Clock clock(@Value("${app.zone}") String zone) {
        return Clock.tick(Clock.system(ZoneId.of(zone)), Duration.ofNanos(1000));
    }

    @Bean
    public NewTopic receiptTopic() {
        return TopicBuilder.name(RECEIPT_TOPIC).partitions(3).replicas(1).build();
    }

    /**
     * Client verso Account: timeout brevi, se Account non risponde preferisco fallire subito.
     */
    @Bean
    public RestClient accountRestClient(RestClient.Builder builder, @Value("${account.base-url}") String baseUrl) {
        return builder
                .baseUrl(baseUrl)
                .requestFactory(requestFactory(1000, 2000))
                .requestInterceptor(CorrelationId.propagatingInterceptor())
                .build();
    }

    /**
     * Client verso il servizio esterno di trasmissione (lo stub).
     */
    @Bean
    public RestClient transmissionRestClient(RestClient.Builder builder,
                                             @Value("${transmission.base-url}") String baseUrl,
                                             @Value("${transmission.connect-timeout-ms}") int connectTimeout,
                                             @Value("${transmission.read-timeout-ms}") int readTimeout) {
        return builder
                .baseUrl(baseUrl)
                .requestFactory(requestFactory(connectTimeout, readTimeout))
                .requestInterceptor(CorrelationId.propagatingInterceptor())
                .build();
    }

    private SimpleClientHttpRequestFactory requestFactory(int connectTimeoutMs, int readTimeoutMs) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofMillis(connectTimeoutMs));
        factory.setReadTimeout(Duration.ofMillis(readTimeoutMs));
        return factory;
    }
}
