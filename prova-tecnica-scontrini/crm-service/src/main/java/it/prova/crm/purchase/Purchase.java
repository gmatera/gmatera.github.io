package it.prova.crm.purchase;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Un acquisto di un cliente. L'id è l'id del documento di vendita, quindi lo stesso
 * scontrino non può mai essere contato due volte.
 */
@Document("purchases")
@CompoundIndex(name = "org_customer", def = "{'organizationId': 1, 'customerId': 1}")
public class Purchase {

    @Id
    private String receiptId;

    private String organizationId;

    private String customerId;

    /** null se è arrivato prima l'annullo dell'emissione */
    private BigDecimal totalAmount;

    private Instant issuedAt;

    private boolean cancelled;

    public Purchase() {
    }

    /** l'acquisto conta solo se l'emissione è arrivata e non è stato annullato */
    public boolean counts() {
        return issuedAt != null && !cancelled;
    }

    public String getReceiptId() {
        return receiptId;
    }

    public String getOrganizationId() {
        return organizationId;
    }

    public String getCustomerId() {
        return customerId;
    }

    public BigDecimal getTotalAmount() {
        return totalAmount;
    }

    public Instant getIssuedAt() {
        return issuedAt;
    }

    public boolean isCancelled() {
        return cancelled;
    }
}
