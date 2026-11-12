package it.prova.receipts.receipt;

import it.prova.common.ApiException;
import it.prova.receipts.account.AccountClient;
import it.prova.receipts.config.AppConfig;
import it.prova.receipts.outbox.OutboxService;
import it.prova.receipts.receipt.ReceiptController.CreateReceiptRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Service
public class ReceiptService {

    private static final Logger log = LoggerFactory.getLogger(ReceiptService.class);

    private final ReceiptRepository receiptRepository;
    private final ReceiptNumberGenerator numberGenerator;
    private final AccountClient accountClient;
    private final OutboxService outboxService;
    private final TransactionTemplate transactionTemplate;
    private final Clock clock;

    public ReceiptService(ReceiptRepository receiptRepository,
                          ReceiptNumberGenerator numberGenerator,
                          AccountClient accountClient,
                          OutboxService outboxService,
                          TransactionTemplate transactionTemplate,
                          Clock clock) {
        this.receiptRepository = receiptRepository;
        this.numberGenerator = numberGenerator;
        this.accountClient = accountClient;
        this.outboxService = outboxService;
        this.transactionTemplate = transactionTemplate;
        this.clock = clock;
    }

    /**
     * Emissione di un documento di vendita.
     * Uso TransactionTemplate (e non @Transactional) per avere la transazione solo attorno alla
     * scrittura e poter gestire fuori dalla transazione il caso della chiave di idempotenza duplicata.
     */
    public IssueResult issue(String idempotencyKey, CreateReceiptRequest request) {
        validate(idempotencyKey, request);

        // 1. la chiave è già stata usata: restituisco il documento già emesso, senza emetterne un altro
        Optional<Receipt> existing = receiptRepository.findByOrganizationIdAndIdempotencyKey(request.organizationId(), idempotencyKey);
        if (existing.isPresent()) {
            log.info("Chiave di idempotenza {} già usata, restituisco il documento {}", idempotencyKey, existing.get().getId());
            return new IssueResult(existing.get(), false);
        }

        // 2. l'organizzazione deve essere attiva e abilitata a SCONTRINA (chiamata ad Account, fuori transazione)
        accountClient.checkCanIssue(request.organizationId());

        // 3. numerazione + salvataggio + evento nella stessa transazione
        try {
            Receipt receipt = transactionTemplate.execute(status -> createSale(idempotencyKey, request));
            log.info("Emesso documento {} n. {} per il punto cassa {}", receipt.getId(), receipt.getNumber(), receipt.getCashRegisterId());
            return new IssueResult(receipt, true);
        } catch (DataIntegrityViolationException e) {
            // due richieste con la stessa chiave nello stesso momento: il vincolo unique fa vincere la prima,
            // la seconda va in rollback (anche il numero) e restituisce il documento della prima
            return receiptRepository.findByOrganizationIdAndIdempotencyKey(request.organizationId(), idempotencyKey)
                    .map(receipt -> new IssueResult(receipt, false))
                    .orElseThrow(() -> e);
        }
    }

    /**
     * Annullamento: non modifica né cancella l'originale, emette un nuovo documento collegato.
     */
    public Receipt cancel(Long receiptId) {
        Receipt original = get(receiptId);
        if (original.getType() == ReceiptType.CANCELLATION) {
            throw ApiException.badRequest("CANNOT_CANCEL_CANCELLATION", "Un documento di annullo non può essere annullato");
        }
        if (receiptRepository.existsByOriginalReceiptId(receiptId)) {
            throw alreadyCancelled(receiptId);
        }

        accountClient.checkCanIssue(original.getOrganizationId());

        try {
            Receipt cancellation = transactionTemplate.execute(status -> createCancellation(original));
            log.info("Documento {} annullato con il documento {}", receiptId, cancellation.getId());
            return cancellation;
        } catch (DataIntegrityViolationException e) {
            // annullo concorrente: il vincolo unique su original_receipt_id ne lascia passare uno solo
            throw alreadyCancelled(receiptId);
        }
    }

    public Receipt get(Long id) {
        return receiptRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("RECEIPT_NOT_FOUND", "Documento " + id + " non trovato"));
    }

    public List<Receipt> findByDay(String organizationId, String cashRegisterId, LocalDate date) {
        return receiptRepository.findByOrganizationIdAndCashRegisterIdAndBusinessDateOrderByNumber(organizationId, cashRegisterId, date);
    }

    private Receipt createSale(String idempotencyKey, CreateReceiptRequest request) {
        int number = numberGenerator.nextNumber(request.organizationId(), request.cashRegisterId());
        List<ReceiptLine> lines = request.lines().stream()
                .map(l -> new ReceiptLine(l.description(), l.quantity(), l.unitPrice(), l.vatRate()))
                .toList();
        Instant now = Instant.now(clock);

        Receipt receipt = Receipt.sale(request.organizationId(), request.cashRegisterId(), number,
                request.paymentType(), request.customerId(), lines, idempotencyKey, now, LocalDate.now(clock));
        receiptRepository.save(receipt);

        outboxService.add(AppConfig.RECEIPT_TOPIC, receipt.getOrganizationId(), ReceiptEvent.ISSUED, ReceiptEvent.of(receipt));
        return receipt;
    }

    private Receipt createCancellation(Receipt original) {
        int number = numberGenerator.nextNumber(original.getOrganizationId(), original.getCashRegisterId());
        Receipt cancellation = Receipt.cancellationOf(original, number, Instant.now(clock), LocalDate.now(clock));
        receiptRepository.save(cancellation);

        outboxService.add(AppConfig.RECEIPT_TOPIC, cancellation.getOrganizationId(), ReceiptEvent.CANCELLED, ReceiptEvent.of(cancellation));
        return cancellation;
    }

    private void validate(String idempotencyKey, CreateReceiptRequest request) {
        if (idempotencyKey.isBlank() || idempotencyKey.length() > 100) {
            throw ApiException.badRequest("INVALID_IDEMPOTENCY_KEY", "L'header Idempotency-Key deve avere da 1 a 100 caratteri");
        }
        for (ReceiptController.LineRequest line : request.lines()) {
            if (!ReceiptCalculator.ALLOWED_VAT_RATES.contains(line.vatRate())) {
                throw ApiException.badRequest("INVALID_VAT_RATE", "Aliquota IVA " + line.vatRate() + "% non ammessa (valori ammessi: 4, 10, 22)");
            }
        }
    }

    private ApiException alreadyCancelled(Long receiptId) {
        return ApiException.conflict("RECEIPT_ALREADY_CANCELLED", "Il documento " + receiptId + " è già stato annullato");
    }

    public record IssueResult(Receipt receipt, boolean created) {
    }
}
