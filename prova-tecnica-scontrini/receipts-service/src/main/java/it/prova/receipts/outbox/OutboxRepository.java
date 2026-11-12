package it.prova.receipts.outbox;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface OutboxRepository extends JpaRepository<OutboxEvent, Long> {

    /**
     * Prende i prossimi eventi da pubblicare bloccandoli.
     * SKIP LOCKED: se ci sono più istanze, ognuna salta le righe già prese da un'altra.
     */
    @Query(value = """
            SELECT * FROM outbox_event
            WHERE published_at IS NULL
            ORDER BY id
            LIMIT 100
            FOR UPDATE SKIP LOCKED
            """, nativeQuery = true)
    List<OutboxEvent> lockNextBatch();
}
