package it.prova.crm.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.core.convert.MongoCustomConversions;

@Configuration
public class MongoConfig {

    /**
     * Di default Spring Data salva i BigDecimal come stringhe: li salvo come Decimal128
     * così in Mongo gli importi restano numeri (utile per query e aggregazioni).
     */
    @Bean
    public MongoCustomConversions mongoCustomConversions() {
        return MongoCustomConversions.create(config ->
                config.bigDecimal(MongoCustomConversions.BigDecimalRepresentation.DECIMAL128));
    }
}
