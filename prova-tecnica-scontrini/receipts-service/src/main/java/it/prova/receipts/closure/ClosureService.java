package it.prova.receipts.closure;

import it.prova.common.ApiException;
import it.prova.receipts.receipt.Receipt;
import it.prova.receipts.receipt.ReceiptRepository;
import it.prova.receipts.receipt.ReceiptType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Service
public class ClosureService {

    private static final Logger log = LoggerFactory.getLogger(ClosureService.class);

    private final DailyClosureRepository closureRepository;
    private final ReceiptRepository receiptRepository;
    private final Clock clock;

    public ClosureService(DailyClosureRepository closureRepository, ReceiptRepository receiptRepository, Clock clock) {
        this.closureRepository = closureRepository;
        this.receiptRepository = receiptRepository;
        this.clock = clock;
    }

    /**
     * Crea la chiusura di un punto cassa per un giorno già concluso.
     * Se esiste già la restituisce: la stessa chiusura non può essere creata (e quindi trasmessa) due volte.
     */
    public DailyClosure create(String organizationId, String cashRegisterId, LocalDate businessDate) {
        // solo giorni passati: così l'elenco dei documenti non può più cambiare
        if (!businessDate.isBefore(LocalDate.now(clock))) {
            throw ApiException.badRequest("CLOSURE_DATE_NOT_PAST", "La chiusura si può fare solo per giorni già conclusi");
        }

        Optional<DailyClosure> existing = closureRepository.findByOrganizationIdAndCashRegisterIdAndBusinessDate(organizationId, cashRegisterId, businessDate);
        if (existing.isPresent()) {
            return existing.get();
        }

        List<Receipt> receipts = receiptRepository.findByOrganizationIdAndCashRegisterIdAndBusinessDateOrderByNumber(
                organizationId, cashRegisterId, businessDate);
        BigDecimal total = receipts.stream()
                .map(r -> r.getType() == ReceiptType.SALE ? r.getTotalAmount() : r.getTotalAmount().negate())
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        try {
            DailyClosure closure = closureRepository.save(
                    new DailyClosure(organizationId, cashRegisterId, businessDate, receipts.size(), total, Instant.now(clock)));
            log.info("Creata chiusura {} per il punto cassa {} del {}", closure.getId(), cashRegisterId, businessDate);
            return closure;
        } catch (DataIntegrityViolationException e) {
            // creata nello stesso momento da un'altra richiesta/istanza (vincolo unique)
            return closureRepository.findByOrganizationIdAndCashRegisterIdAndBusinessDate(organizationId, cashRegisterId, businessDate)
                    .orElseThrow(() -> e);
        }
    }

    /**
     * Ogni notte crea le chiusure del giorno prima per tutti i punti cassa che hanno emesso documenti.
     * Se gira due volte o su due istanze non succede nulla di male: create() è idempotente.
     */
    @Scheduled(cron = "${closure.cron}", zone = "${app.zone}")
    public void createYesterdayClosures() {
        LocalDate yesterday = LocalDate.now(clock).minusDays(1);
        for (CashRegisterRef register : receiptRepository.findCashRegistersWithReceipts(yesterday)) {
            create(register.organizationId(), register.cashRegisterId(), yesterday);
        }
    }

    public DailyClosure get(Long id) {
        return closureRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("CLOSURE_NOT_FOUND", "Chiusura " + id + " non trovata"));
    }

    public List<DailyClosure> findByOrganization(String organizationId) {
        return closureRepository.findByOrganizationIdOrderByBusinessDateDesc(organizationId);
    }
}
