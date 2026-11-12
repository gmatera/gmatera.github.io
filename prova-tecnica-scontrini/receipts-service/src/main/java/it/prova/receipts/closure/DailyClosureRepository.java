package it.prova.receipts.closure;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface DailyClosureRepository extends JpaRepository<DailyClosure, Long> {

    Optional<DailyClosure> findByOrganizationIdAndCashRegisterIdAndBusinessDate(
            String organizationId, String cashRegisterId, LocalDate businessDate);

    List<DailyClosure> findByStatusAndNextAttemptAtLessThanEqualOrderByNextAttemptAt(ClosureStatus status, Instant now);

    List<DailyClosure> findByOrganizationIdOrderByBusinessDateDesc(String organizationId);

    /**
     * "Prenota" la chiusura per l'invio. L'UPDATE è condizionato allo stato TO_SEND, quindi
     * se due esecuzioni (o due istanze) ci provano insieme solo una ottiene 1 riga aggiornata.
     * Ha una sua transazione, così lo stato IN_PROGRESS è subito visibile alle altre istanze.
     */
    @Transactional
    @Modifying
    @Query(value = """
            UPDATE daily_closure
            SET status = 'IN_PROGRESS', attempts = attempts + 1, updated_at = :now
            WHERE id = :id AND status = 'TO_SEND'
            """, nativeQuery = true)
    int claim(Long id, Instant now);
}
