package it.prova.crm.purchase;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Evento di Receipts come lo vede il CRM.
 */
public record ReceiptEvent(
        String eventId,
        String eventType,
        Long receiptId,
        Long originalReceiptId,
        String organizationId,
        String customerId,
        BigDecimal totalAmount,
        Instant issuedAt
) {
    public static final String ISSUED = "RECEIPT_ISSUED";
    public static final String CANCELLED = "RECEIPT_CANCELLED";
}
