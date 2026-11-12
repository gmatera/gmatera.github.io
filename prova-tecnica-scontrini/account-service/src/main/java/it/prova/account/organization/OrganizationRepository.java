package it.prova.account.organization;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;

public interface OrganizationRepository extends JpaRepository<Organization, String> {

    /**
     * SELECT ... FOR UPDATE sull'organizzazione: lo uso per serializzare le modifiche
     * alle abilitazioni della stessa organizzazione.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from Organization o where o.id = :id")
    Optional<Organization> findByIdForUpdate(String id);
}
