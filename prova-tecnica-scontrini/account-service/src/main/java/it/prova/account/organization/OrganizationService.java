package it.prova.account.organization;

import it.prova.common.ApiException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

@Service
public class OrganizationService {

    private final OrganizationRepository repository;
    private final Clock clock;

    public OrganizationService(OrganizationRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    @Transactional
    public Organization create(String name) {
        return repository.save(new Organization(name, Instant.now(clock)));
    }

    @Transactional(readOnly = true)
    public Organization get(String id) {
        return repository.findById(id)
                .orElseThrow(() -> ApiException.notFound("ORGANIZATION_NOT_FOUND", "Organizzazione " + id + " non trovata"));
    }

    @Transactional(readOnly = true)
    public List<Organization> findAll() {
        return repository.findAll();
    }

    @Transactional
    public Organization changeStatus(String id, OrganizationStatus status) {
        Organization organization = get(id);
        organization.changeStatus(status);
        return organization;
    }
}
