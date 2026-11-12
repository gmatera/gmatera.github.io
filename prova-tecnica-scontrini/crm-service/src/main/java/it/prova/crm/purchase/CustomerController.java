package it.prova.crm.purchase;

import it.prova.crm.purchase.PurchaseService.CustomerSummary;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

@RestController
@RequestMapping("/organizations/{organizationId}/customers/{customerId}")
public class CustomerController {

    private final PurchaseService service;

    public CustomerController(PurchaseService service) {
        this.service = service;
    }

    /** numero acquisti, totale speso, ultimo acquisto */
    @GetMapping("/summary")
    public CustomerSummary summary(@PathVariable String organizationId, @PathVariable String customerId) {
        return service.summary(organizationId, customerId);
    }

    @GetMapping("/purchases")
    public List<PurchaseResponse> purchases(@PathVariable String organizationId, @PathVariable String customerId) {
        return service.purchases(organizationId, customerId).stream()
                .map(p -> new PurchaseResponse(p.getReceiptId(), p.getTotalAmount(), p.getIssuedAt()))
                .toList();
    }

    public record PurchaseResponse(String receiptId, BigDecimal totalAmount, Instant issuedAt) {
    }
}
