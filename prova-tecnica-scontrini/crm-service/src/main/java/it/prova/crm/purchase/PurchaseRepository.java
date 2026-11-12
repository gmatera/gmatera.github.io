package it.prova.crm.purchase;

import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface PurchaseRepository extends MongoRepository<Purchase, String> {

    List<Purchase> findByOrganizationIdAndCustomerIdOrderByIssuedAtDesc(String organizationId, String customerId);
}
