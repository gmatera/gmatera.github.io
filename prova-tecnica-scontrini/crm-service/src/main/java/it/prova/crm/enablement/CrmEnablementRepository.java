package it.prova.crm.enablement;

import org.springframework.data.mongodb.repository.MongoRepository;

public interface CrmEnablementRepository extends MongoRepository<CrmEnablement, String> {
}
