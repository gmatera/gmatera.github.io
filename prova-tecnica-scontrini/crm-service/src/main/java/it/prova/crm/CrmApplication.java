package it.prova.crm;

import it.prova.common.CorrelationIdFilter;
import it.prova.common.GlobalExceptionHandler;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Import;

@SpringBootApplication
@Import({CorrelationIdFilter.class, GlobalExceptionHandler.class})
public class CrmApplication {

    public static void main(String[] args) {
        SpringApplication.run(CrmApplication.class, args);
    }
}
