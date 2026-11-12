package it.prova.account.enablement;

import it.prova.account.config.AppConfig;
import it.prova.account.organization.Organization;
import it.prova.account.organization.OrganizationRepository;
import it.prova.account.outbox.OutboxService;
import it.prova.common.ApiException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Service
public class EnablementService {

    private static final Logger log = LoggerFactory.getLogger(EnablementService.class);

    private final EnablementRepository enablementRepository;
    private final OrganizationRepository organizationRepository;
    private final OutboxService outboxService;
    private final Clock clock;

    public EnablementService(EnablementRepository enablementRepository,
                             OrganizationRepository organizationRepository,
                             OutboxService outboxService,
                             Clock clock) {
        this.enablementRepository = enablementRepository;
        this.organizationRepository = organizationRepository;
        this.outboxService = outboxService;
        this.clock = clock;
    }

    /**
     * Provisioning idempotente: la richiesta descrive lo stato desiderato.
     * Se l'abilitazione è già in quello stato non faccio nulla (nessun errore e nessun evento),
     * altrimenti la creo/aggiorno e scrivo l'evento nella outbox, nella stessa transazione.
     */
    @Transactional
    public Enablement provision(String organizationId, Product product,
                                EnablementStatus status, LocalDate startDate, LocalDate endDate) {
        if (endDate != null && endDate.isBefore(startDate)) {
            throw ApiException.badRequest("INVALID_DATES", "La data di fine non può essere precedente alla data di inizio");
        }

        // blocco la riga dell'organizzazione: due provisioning concorrenti sulla stessa
        // organizzazione vengono eseguiti uno dopo l'altro (niente doppi insert)
        organizationRepository.findByIdForUpdate(organizationId)
                .orElseThrow(() -> organizationNotFound(organizationId));

        Optional<Enablement> existing = enablementRepository.findByOrganizationIdAndProduct(organizationId, product);
        if (existing.isPresent() && existing.get().hasSameValues(status, startDate, endDate)) {
            log.info("Abilitazione {} per {} già in questo stato, nessuna modifica", product, organizationId);
            return existing.get();
        }

        Enablement enablement = existing.orElseGet(() -> new Enablement(organizationId, product));
        enablement.update(status, startDate, endDate, Instant.now(clock));
        enablementRepository.save(enablement);

        outboxService.add(AppConfig.ENABLEMENT_TOPIC, organizationId,
                EnablementChangedEvent.TYPE, EnablementChangedEvent.from(enablement));

        log.info("Abilitazione {} per {} aggiornata a {}", product, organizationId, status);
        return enablement;
    }

    @Transactional(readOnly = true)
    public List<Enablement> findByOrganization(String organizationId) {
        if (!organizationRepository.existsById(organizationId)) {
            throw organizationNotFound(organizationId);
        }
        return enablementRepository.findByOrganizationId(organizationId);
    }

    /**
     * Usato dagli altri servizi (Receipts) per sapere se un'organizzazione può usare un prodotto oggi.
     */
    @Transactional(readOnly = true)
    public AccessResponse checkAccess(String organizationId, Product product) {
        Organization organization = organizationRepository.findById(organizationId)
                .orElseThrow(() -> organizationNotFound(organizationId));

        if (!organization.isActive()) {
            return AccessResponse.denied(organizationId, product, "ORGANIZATION_SUSPENDED");
        }

        LocalDate today = LocalDate.now(clock);
        boolean enabled = enablementRepository.findByOrganizationIdAndProduct(organizationId, product)
                .map(e -> e.isActiveOn(today))
                .orElse(false);

        log.info("Verifica accesso di {} al prodotto {}: {}", organizationId, product, enabled ? "consentito" : "negato");
        return enabled
                ? AccessResponse.allowed(organizationId, product)
                : AccessResponse.denied(organizationId, product, "PRODUCT_NOT_ENABLED");
    }

    private ApiException organizationNotFound(String organizationId) {
        return ApiException.notFound("ORGANIZATION_NOT_FOUND", "Organizzazione " + organizationId + " non trovata");
    }

    public record AccessResponse(String organizationId, Product product, boolean allowed, String reason) {
        static AccessResponse allowed(String organizationId, Product product) {
            return new AccessResponse(organizationId, product, true, null);
        }

        static AccessResponse denied(String organizationId, Product product, String reason) {
            return new AccessResponse(organizationId, product, false, reason);
        }
    }
}
