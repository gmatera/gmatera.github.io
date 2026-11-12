package it.prova.receipts.closure;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/**
 * Chiusura giornaliera di un punto cassa e stato della sua trasmissione all'ente esterno.
 */
@Entity
@Table(name = "daily_closure")
public class DailyClosure {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String organizationId;

    private String cashRegisterId;

    private LocalDate businessDate;

    private int receiptCount;

    private BigDecimal totalAmount;

    @Enumerated(EnumType.STRING)
    private ClosureStatus status;

    private int attempts;

    private Instant nextAttemptAt;

    private String lastError;

    private String protocolNumber;

    private Instant createdAt;

    private Instant updatedAt;

    protected DailyClosure() {
        // per JPA
    }

    public DailyClosure(String organizationId, String cashRegisterId, LocalDate businessDate,
                        int receiptCount, BigDecimal totalAmount, Instant now) {
        this.organizationId = organizationId;
        this.cashRegisterId = cashRegisterId;
        this.businessDate = businessDate;
        this.receiptCount = receiptCount;
        this.totalAmount = totalAmount;
        this.status = ClosureStatus.TO_SEND;
        this.attempts = 0;
        this.nextAttemptAt = now;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public void markSent(String protocolNumber, Instant now) {
        this.status = ClosureStatus.SENT;
        this.protocolNumber = protocolNumber;
        this.lastError = null;
        this.updatedAt = now;
    }

    public void retryLater(String error, Instant nextAttemptAt, Instant now) {
        this.status = ClosureStatus.TO_SEND;
        this.lastError = truncate(error);
        this.nextAttemptAt = nextAttemptAt;
        this.updatedAt = now;
    }

    public void markFailed(String error, Instant now) {
        this.status = ClosureStatus.FAILED;
        this.lastError = truncate(error);
        this.updatedAt = now;
    }

    private static String truncate(String error) {
        if (error == null) {
            return null;
        }
        return error.length() > 500 ? error.substring(0, 500) : error;
    }

    public Long getId() {
        return id;
    }

    public String getOrganizationId() {
        return organizationId;
    }

    public String getCashRegisterId() {
        return cashRegisterId;
    }

    public LocalDate getBusinessDate() {
        return businessDate;
    }

    public int getReceiptCount() {
        return receiptCount;
    }

    public BigDecimal getTotalAmount() {
        return totalAmount;
    }

    public ClosureStatus getStatus() {
        return status;
    }

    public int getAttempts() {
        return attempts;
    }

    public Instant getNextAttemptAt() {
        return nextAttemptAt;
    }

    public String getLastError() {
        return lastError;
    }

    public String getProtocolNumber() {
        return protocolNumber;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
