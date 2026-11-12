package it.prova.receipts.receipt;

import it.prova.receipts.receipt.ReceiptCalculator.Totals;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ReceiptCalculatorTest {

    @Test
    void lineTotalIsQuantityTimesUnitPrice() {
        assertThat(ReceiptCalculator.lineTotal(3, new BigDecimal("0.33"))).isEqualByComparingTo("0.99");
    }

    @Test
    void vatIsExtractedFromGrossPrice() {
        Totals totals = ReceiptCalculator.computeTotals(List.of(line(1, "12.20", 22)));

        assertThat(totals.total()).isEqualByComparingTo("12.20");
        assertThat(totals.vat()).isEqualByComparingTo("2.20");
    }

    @Test
    void differentRatesAreComputedSeparatelyAndSummed() {
        Totals totals = ReceiptCalculator.computeTotals(List.of(
                line(2, "1.10", 10),   // 2.20 -> imponibile 2.00, IVA 0.20
                line(1, "5.20", 4),    // 5.20 -> imponibile 5.00, IVA 0.20
                line(1, "12.20", 22))); // 12.20 -> imponibile 10.00, IVA 2.20

        assertThat(totals.total()).isEqualByComparingTo("19.60");
        assertThat(totals.vat()).isEqualByComparingTo("2.60");
    }

    @Test
    void linesWithTheSameRateAreGroupedBeforeRounding() {
        // tre righe da 0.10 al 22%: lordo 0.30 -> imponibile 0.25 (0.2459 arrotondato), IVA 0.05
        // calcolando riga per riga verrebbe 3 x 0.02 = 0.06
        Totals totals = ReceiptCalculator.computeTotals(List.of(
                line(1, "0.10", 22),
                line(1, "0.10", 22),
                line(1, "0.10", 22)));

        assertThat(totals.total()).isEqualByComparingTo("0.30");
        assertThat(totals.vat()).isEqualByComparingTo("0.05");
    }

    @Test
    void netIsRoundedHalfUp() {
        // 1.00 al 10%: imponibile 0.90909 -> 0.91, IVA 0.09
        assertThat(ReceiptCalculator.computeTotals(List.of(line(1, "1.00", 10))).vat()).isEqualByComparingTo("0.09");

        // 0.13 al 4%: imponibile esatto 0.125 -> HALF_UP 0.13, quindi IVA 0.00
        assertThat(ReceiptCalculator.computeTotals(List.of(line(1, "0.13", 4))).vat()).isEqualByComparingTo("0.00");
    }

    private static ReceiptLine line(int quantity, String unitPrice, int vatRate) {
        return new ReceiptLine("articolo", quantity, new BigDecimal(unitPrice), vatRate);
    }
}
