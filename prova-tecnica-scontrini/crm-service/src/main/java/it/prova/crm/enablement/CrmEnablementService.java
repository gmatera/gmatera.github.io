package it.prova.crm.enablement;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.Optional;

@Service
public class CrmEnablementService {

    private static final Logger log = LoggerFactory.getLogger(CrmEnablementService.class);

    private final CrmEnablementRepository repository;

    public CrmEnablementService(CrmEnablementRepository repository) {
        this.repository = repository;
    }

    public void onEnablementChanged(EnablementEvent event) {
        if (!"CRM".equals(event.product())) {
            return; // al CRM interessa solo l'abilitazione al prodotto CRM
        }

        // l'evento contiene lo stato completo: applico solo se è più recente di quello che ho già,
        // così un evento duplicato o arrivato in ritardo non sovrascrive uno stato più nuovo
        Optional<CrmEnablement> current = repository.findById(event.organizationId());
        if (current.isPresent() && !event.occurredAt().isAfter(current.get().getLastEventAt())) {
            log.info("Evento abilitazione {} vecchio o duplicato, ignorato", event.eventId());
            return;
        }

        repository.save(new CrmEnablement(event.organizationId(), event.status(),
                event.startDate(), event.endDate(), event.occurredAt()));
        log.info("Abilitazione CRM di {} aggiornata a {}", event.organizationId(), event.status());
    }

    public boolean isEnabled(String organizationId, LocalDate date) {
        return repository.findById(organizationId)
                .map(e -> e.isActiveOn(date))
                .orElse(false);
    }
}
