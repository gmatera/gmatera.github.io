package it.prova.receipts.receipt;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Numerazione progressiva per punto cassa, senza buchi né duplicati.
 * <p>
 * L'upsert incrementa il contatore e lascia la riga bloccata (lock esclusivo di MySQL) fino alla fine
 * della transazione: un'altra emissione sullo stesso punto cassa aspetta che la prima finisca.
 * Se la transazione va in rollback torna indietro anche l'incremento, quindi non restano buchi.
 * Punti cassa diversi non si bloccano a vicenda.
 */
@Component
public class ReceiptNumberGenerator {

    private final JdbcTemplate jdbcTemplate;

    public ReceiptNumberGenerator(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public int nextNumber(String organizationId, String cashRegisterId) {
        jdbcTemplate.update("""
                INSERT INTO cash_register_counter (organization_id, cash_register_id, last_number)
                VALUES (?, ?, 1)
                ON DUPLICATE KEY UPDATE last_number = last_number + 1
                """, organizationId, cashRegisterId);

        return jdbcTemplate.queryForObject("""
                SELECT last_number FROM cash_register_counter
                WHERE organization_id = ? AND cash_register_id = ?
                """, Integer.class, organizationId, cashRegisterId);
    }
}
