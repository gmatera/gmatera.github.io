package it.prova.stub;

import it.prova.common.CorrelationIdFilter;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Import;

@SpringBootApplication
@Import(CorrelationIdFilter.class)
public class StubApplication {

    public static void main(String[] args) {
        SpringApplication.run(StubApplication.class, args);
    }
}
