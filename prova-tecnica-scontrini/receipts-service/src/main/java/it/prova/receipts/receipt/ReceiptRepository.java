package it.prova.receipts.receipt;

import it.prova.receipts.closure.CashRegisterRef;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface ReceiptRepository extends JpaRepository<Receipt, Long> {

    Optional<Receipt> findByOrganizationIdAndIdempotencyKey(String organizationId, String idempotencyKey);

    boolean existsByOriginalReceiptId(Long originalReceiptId);

    List<Receipt> findByOrganizationIdAndCashRegisterIdAndBusinessDateOrderByNumber(
            String organizationId, String cashRegisterId, LocalDate businessDate);

    @Query("""
            select distinct new it.prova.receipts.closure.CashRegisterRef(r.organizationId, r.cashRegisterId)
            from Receipt r
            where r.businessDate = :date
            """)
    List<CashRegisterRef> findCashRegistersWithReceipts(LocalDate date);
}
