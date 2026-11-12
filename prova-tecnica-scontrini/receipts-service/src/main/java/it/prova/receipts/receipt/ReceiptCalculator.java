package it.prova.receipts.receipt;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * Calcolo di totale e IVA. Regola di arrotondamento (vedi anche il README):
 * <ul>
 *     <li>i prezzi unitari sono IVA inclusa, con al massimo 2 decimali;</li>
 *     <li>totale riga = quantità x prezzo unitario (resta a 2 decimali, nessun arrotondamento);</li>
 *     <li>l'IVA si calcola per aliquota: sommo i totali delle righe con la stessa aliquota e
 *     scorporo l'imponibile = lordo x 100 / (100 + aliquota), arrotondato a 2 decimali HALF_UP;</li>
 *     <li>IVA = lordo - imponibile, così imponibile + IVA torna sempre al centesimo con il totale.</li>
 * </ul>
 */
public final class ReceiptCalculator {

    public static final Set<Integer> ALLOWED_VAT_RATES = Set.of(4, 10, 22);

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private ReceiptCalculator() {
    }

    public static BigDecimal lineTotal(int quantity, BigDecimal unitPrice) {
        return unitPrice.multiply(BigDecimal.valueOf(quantity)).setScale(2, RoundingMode.HALF_UP);
    }

    public static Totals computeTotals(List<ReceiptLine> lines) {
        // lordo per aliquota
        Map<Integer, BigDecimal> grossByRate = new TreeMap<>();
        for (ReceiptLine line : lines) {
            grossByRate.merge(line.getVatRate(), line.getLineTotal(), BigDecimal::add);
        }

        BigDecimal total = BigDecimal.ZERO.setScale(2);
        BigDecimal vat = BigDecimal.ZERO.setScale(2);
        for (Map.Entry<Integer, BigDecimal> entry : grossByRate.entrySet()) {
            BigDecimal gross = entry.getValue();
            BigDecimal net = gross.multiply(HUNDRED)
                    .divide(HUNDRED.add(BigDecimal.valueOf(entry.getKey())), 2, RoundingMode.HALF_UP);
            total = total.add(gross);
            vat = vat.add(gross.subtract(net));
        }
        return new Totals(total, vat);
    }

    public record Totals(BigDecimal total, BigDecimal vat) {
    }
}
