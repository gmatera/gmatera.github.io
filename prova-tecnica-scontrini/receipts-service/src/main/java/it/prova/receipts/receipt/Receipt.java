package it.prova.receipts.receipt;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Documento commerciale (scontrino). Una volta emesso non viene più modificato:
 * l'annullamento crea un nuovo documento di tipo CANCELLATION collegato all'originale.
 */
@Entity
@Table(name = "receipt")
public class Receipt {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String organizationId;

    private String cashRegisterId;

    @Column(name = "receipt_number")
    private int number;

    @Enumerated(EnumType.STRING)
    private ReceiptType type;

    @Enumerated(EnumType.STRING)
    private PaymentType paymentType;

    private String customerId;

    private BigDecimal totalAmount;

    private BigDecimal vatAmount;

    private Instant issuedAt;

    /** giorno lavorativo (ora italiana), usato per la chiusura giornaliera */
    private LocalDate businessDate;

    private String idempotencyKey;

    private Long originalReceiptId;

    @OneToMany(cascade = CascadeType.ALL, fetch = FetchType.EAGER)
    @JoinColumn(name = "receipt_id", nullable = false)
    @OrderBy("id")
    private List<ReceiptLine> lines = new ArrayList<>();

    protected Receipt() {
        // per JPA
    }

    private Receipt(String organizationId, String cashRegisterId, int number, ReceiptType type,
                    PaymentType paymentType, String customerId, List<ReceiptLine> lines,
                    Instant issuedAt, LocalDate businessDate) {
        this.organizationId = organizationId;
        this.cashRegisterId = cashRegisterId;
        this.number = number;
        this.type = type;
        this.paymentType = paymentType;
        this.customerId = customerId;
        this.lines = new ArrayList<>(lines);
        this.issuedAt = issuedAt;
        this.businessDate = businessDate;

        ReceiptCalculator.Totals totals = ReceiptCalculator.computeTotals(lines);
        this.totalAmount = totals.total();
        this.vatAmount = totals.vat();
    }

    public static Receipt sale(String organizationId, String cashRegisterId, int number, PaymentType paymentType,
                               String customerId, List<ReceiptLine> lines, String idempotencyKey,
                               Instant issuedAt, LocalDate businessDate) {
        Receipt receipt = new Receipt(organizationId, cashRegisterId, number, ReceiptType.SALE,
                paymentType, customerId, lines, issuedAt, businessDate);
        receipt.idempotencyKey = idempotencyKey;
        return receipt;
    }

    public static Receipt cancellationOf(Receipt original, int number, Instant issuedAt, LocalDate businessDate) {
        List<ReceiptLine> copiedLines = original.getLines().stream().map(ReceiptLine::copy).toList();
        Receipt receipt = new Receipt(original.organizationId, original.cashRegisterId, number,
                ReceiptType.CANCELLATION, original.paymentType, original.customerId, copiedLines,
                issuedAt, businessDate);
        receipt.originalReceiptId = original.id;
        return receipt;
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

    public int getNumber() {
        return number;
    }

    public ReceiptType getType() {
        return type;
    }

    public PaymentType getPaymentType() {
        return paymentType;
    }

    public String getCustomerId() {
        return customerId;
    }

    public BigDecimal getTotalAmount() {
        return totalAmount;
    }

    public BigDecimal getVatAmount() {
        return vatAmount;
    }

    public Instant getIssuedAt() {
        return issuedAt;
    }

    public LocalDate getBusinessDate() {
        return businessDate;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public Long getOriginalReceiptId() {
        return originalReceiptId;
    }

    public List<ReceiptLine> getLines() {
        return lines;
    }
}
