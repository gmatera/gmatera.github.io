package it.prova.receipts.receipt;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Evento pubblicato su Kafka quando un documento viene emesso o annullato.
 * Per l'annullo receiptId è il documento di annullo e originalReceiptId quello annullato.
 */
public record ReceiptEvent(
        String eventId,
        String eventType,
        Long receiptId,
        Long originalReceiptId,
        String organizationId,
        String cashRegisterId,
        int number,
        String customerId,
        BigDecimal totalAmount,
        Instant issuedAt
) {
    public static final String ISSUED = "RECEIPT_ISSUED";
    public static final String CANCELLED = "RECEIPT_CANCELLED";

    public static ReceiptEvent of(Receipt receipt) {
        String type = receipt.getType() == ReceiptType.SALE ? ISSUED : CANCELLED;
        return new ReceiptEvent(
                UUID.randomUUID().toString(),
                type,
                receipt.getId(),
                receipt.getOriginalReceiptId(),
                receipt.getOrganizationId(),
                receipt.getCashRegisterId(),
                receipt.getNumber(),
                receipt.getCustomerId(),
                receipt.getTotalAmount(),
                receipt.getIssuedAt());
    }
}
