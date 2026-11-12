package it.prova.account.enablement;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface EnablementRepository extends JpaRepository<Enablement, Long> {

    Optional<Enablement> findByOrganizationIdAndProduct(String organizationId, Product product);

    List<Enablement> findByOrganizationId(String organizationId);
}
