package it.prova.crm.purchase;

import it.prova.crm.enablement.CrmEnablementService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

/**
 * Storico acquisti per cliente.
 * <p>
 * Per non falsare i dati con eventi duplicati o fuori ordine ogni scontrino è un documento Mongo
 * con id = id dello scontrino, aggiornato con un upsert:
 * <ul>
 *     <li>emissione: scrive importo e data, senza toccare il flag cancelled;</li>
 *     <li>annullo: mette cancelled = true sull'acquisto originale (lo crea se l'emissione non è ancora arrivata).</li>
 * </ul>
 * Ripetere un evento non cambia nulla e l'ordine di arrivo non conta. I totali si calcolano in lettura.
 */
@Service
public class PurchaseService {

    private static final Logger log = LoggerFactory.getLogger(PurchaseService.class);
    private static final ZoneId ZONE = ZoneId.of("Europe/Rome");

    private final MongoTemplate mongoTemplate;
    private final PurchaseRepository purchaseRepository;
    private final CrmEnablementService enablementService;

    public PurchaseService(MongoTemplate mongoTemplate, PurchaseRepository purchaseRepository,
                           CrmEnablementService enablementService) {
        this.mongoTemplate = mongoTemplate;
        this.purchaseRepository = purchaseRepository;
        this.enablementService = enablementService;
    }

    public void onReceiptIssued(ReceiptEvent event) {
        if (!shouldProcess(event)) {
            return;
        }
        Query query = Query.query(Criteria.where("_id").is(String.valueOf(event.receiptId())));
        Update update = new Update()
                .set("organizationId", event.organizationId())
                .set("customerId", event.customerId())
                .set("totalAmount", event.totalAmount())
                .set("issuedAt", event.issuedAt())
                .setOnInsert("cancelled", false);
        mongoTemplate.upsert(query, update, Purchase.class);
        log.info("Registrato acquisto {} del cliente {}", event.receiptId(), event.customerId());
    }

    public void onReceiptCancelled(ReceiptEvent event) {
        if (!shouldProcess(event)) {
            return;
        }
        Query query = Query.query(Criteria.where("_id").is(String.valueOf(event.originalReceiptId())));
        Update update = new Update()
                .set("organizationId", event.organizationId())
                .set("customerId", event.customerId())
                .set("cancelled", true);
        mongoTemplate.upsert(query, update, Purchase.class);
        log.info("Annullato acquisto {} del cliente {}", event.originalReceiptId(), event.customerId());
    }

    public CustomerSummary summary(String organizationId, String customerId) {
        List<Purchase> purchases = purchases(organizationId, customerId);
        BigDecimal totalSpent = purchases.stream()
                .map(Purchase::getTotalAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        Instant lastPurchaseAt = purchases.stream()
                .map(Purchase::getIssuedAt)
                .max(Instant::compareTo)
                .orElse(null);
        return new CustomerSummary(organizationId, customerId, purchases.size(), totalSpent, lastPurchaseAt);
    }

    /** acquisti validi (emessi e non annullati), dal più recente */
    public List<Purchase> purchases(String organizationId, String customerId) {
        return purchaseRepository.findByOrganizationIdAndCustomerIdOrderByIssuedAtDesc(organizationId, customerId)
                .stream()
                .filter(Purchase::counts)
                .toList();
    }

    private boolean shouldProcess(ReceiptEvent event) {
        if (event.customerId() == null) {
            return false; // scontrino senza cliente: niente storico
        }
        LocalDate eventDate = LocalDate.ofInstant(event.issuedAt(), ZONE);
        if (!enablementService.isEnabled(event.organizationId(), eventDate)) {
            log.info("Organizzazione {} non abilitata a CRM, evento {} ignorato", event.organizationId(), event.eventId());
            return false;
        }
        return true;
    }

    public record CustomerSummary(String organizationId, String customerId, int purchaseCount,
                                  BigDecimal totalSpent, Instant lastPurchaseAt) {
    }
}
