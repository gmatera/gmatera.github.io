package it.prova.receipts.receipt;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;

@Entity
@Table(name = "receipt_line")
public class ReceiptLine {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String description;

    private int quantity;

    /** prezzo unitario IVA inclusa */
    private BigDecimal unitPrice;

    private int vatRate;

    private BigDecimal lineTotal;

    protected ReceiptLine() {
        // per JPA
    }

    public ReceiptLine(String description, int quantity, BigDecimal unitPrice, int vatRate) {
        this.description = description;
        this.quantity = quantity;
        this.unitPrice = unitPrice;
        this.vatRate = vatRate;
        this.lineTotal = ReceiptCalculator.lineTotal(quantity, unitPrice);
    }

    /** copia della riga, usata per il documento di annullo */
    public ReceiptLine copy() {
        return new ReceiptLine(description, quantity, unitPrice, vatRate);
    }

    public Long getId() {
        return id;
    }

    public String getDescription() {
        return description;
    }

    public int getQuantity() {
        return quantity;
    }

    public BigDecimal getUnitPrice() {
        return unitPrice;
    }

    public int getVatRate() {
        return vatRate;
    }

    public BigDecimal getLineTotal() {
        return lineTotal;
    }
}
