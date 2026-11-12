package it.prova.crm.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

@Configuration
public class KafkaConfig {

    public static final String ENABLEMENT_TOPIC = "account.enablement-events";
    public static final String RECEIPT_TOPIC = "receipts.receipt-events";

    // dichiaro i topic anche qui: se il CRM parte prima degli altri servizi vengono creati uguali
    @Bean
    public NewTopic enablementTopic() {
        return TopicBuilder.name(ENABLEMENT_TOPIC).partitions(3).replicas(1).build();
    }

    @Bean
    public NewTopic receiptTopic() {
        return TopicBuilder.name(RECEIPT_TOPIC).partitions(3).replicas(1).build();
    }

    /**
     * Se l'elaborazione di un messaggio fallisce riprovo 3 volte a distanza di 1 secondo,
     * poi lo scarto (viene loggato). Con più tempo lo manderei su un dead letter topic.
     */
    @Bean
    public DefaultErrorHandler errorHandler() {
        return new DefaultErrorHandler(new FixedBackOff(1000L, 3));
    }
}
