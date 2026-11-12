package it.prova.account;

import it.prova.account.enablement.EnablementRepository;
import it.prova.account.enablement.EnablementService;
import it.prova.account.enablement.EnablementService.AccessResponse;
import it.prova.account.enablement.EnablementStatus;
import it.prova.account.enablement.Product;
import it.prova.account.organization.Organization;
import it.prova.account.organization.OrganizationService;
import it.prova.account.organization.OrganizationStatus;
import it.prova.common.ApiException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EnablementServiceIntegrationTest extends IntegrationTestBase {

    private static final LocalDate START = LocalDate.of(2026, 1, 1);

    @Autowired
    private OrganizationService organizationService;

    @Autowired
    private EnablementService enablementService;

    @Autowired
    private EnablementRepository enablementRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void sameProvisioningTwiceCreatesOneEnablementAndOneEvent() {
        Organization org = organizationService.create("Bar Sport srl");

        enablementService.provision(org.getId(), Product.SCONTRINA, EnablementStatus.ENABLED, START, null);
        enablementService.provision(org.getId(), Product.SCONTRINA, EnablementStatus.ENABLED, START, null);

        assertThat(enablementRepository.findByOrganizationId(org.getId())).hasSize(1);
        assertThat(countEvents(org.getId())).isEqualTo(1);
    }

    @Test
    void everyChangeOfStateGeneratesAnEvent() {
        Organization org = organizationService.create("Pizzeria da Mario");

        enablementService.provision(org.getId(), Product.CRM, EnablementStatus.ENABLED, START, null);
        enablementService.provision(org.getId(), Product.CRM, EnablementStatus.DISABLED, START, null);
        enablementService.provision(org.getId(), Product.CRM, EnablementStatus.ENABLED, START, null);

        assertThat(countEvents(org.getId())).isEqualTo(3);
    }

    @Test
    void concurrentProvisioningDoesNotCreateDuplicatesOrErrors() throws Exception {
        Organization org = organizationService.create("Ferramenta Rossi");
        ExecutorService executor = Executors.newFixedThreadPool(8);
        List<Callable<Object>> tasks = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            tasks.add(() -> enablementService.provision(org.getId(), Product.SCONTRINA, EnablementStatus.ENABLED, START, null));
        }

        for (Future<Object> future : executor.invokeAll(tasks)) {
            future.get(); // se un thread fosse andato in errore qui verrebbe rilanciata l'eccezione
        }
        executor.shutdown();

        assertThat(enablementRepository.findByOrganizationId(org.getId())).hasSize(1);
        assertThat(countEvents(org.getId())).isEqualTo(1);
    }

    @Test
    void accessCheckConsidersOrganizationStatusAndEnablement() {
        Organization org = organizationService.create("Libreria Bianchi");

        assertThat(enablementService.checkAccess(org.getId(), Product.SCONTRINA))
                .extracting(AccessResponse::allowed, AccessResponse::reason)
                .containsExactly(false, "PRODUCT_NOT_ENABLED");

        enablementService.provision(org.getId(), Product.SCONTRINA, EnablementStatus.ENABLED, START, null);
        assertThat(enablementService.checkAccess(org.getId(), Product.SCONTRINA).allowed()).isTrue();

        organizationService.changeStatus(org.getId(), OrganizationStatus.SUSPENDED);
        assertThat(enablementService.checkAccess(org.getId(), Product.SCONTRINA))
                .extracting(AccessResponse::allowed, AccessResponse::reason)
                .containsExactly(false, "ORGANIZATION_SUSPENDED");
    }

    @Test
    void provisioningForUnknownOrganizationFails() {
        assertThatThrownBy(() -> enablementService.provision("non-esiste", Product.SCONTRINA,
                EnablementStatus.ENABLED, START, null))
                .isInstanceOf(ApiException.class)
                .hasFieldOrPropertyWithValue("code", "ORGANIZATION_NOT_FOUND");
    }

    @Test
    void endDateBeforeStartDateIsRejected() {
        Organization org = organizationService.create("Edicola Verdi");

        assertThatThrownBy(() -> enablementService.provision(org.getId(), Product.SCONTRINA,
                EnablementStatus.ENABLED, START, START.minusDays(1)))
                .isInstanceOf(ApiException.class)
                .hasFieldOrPropertyWithValue("code", "INVALID_DATES");
    }

    private int countEvents(String organizationId) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM outbox_event WHERE message_key = ?", Integer.class, organizationId);
    }
}
