package it.prova.crm;

import it.prova.crm.enablement.CrmEnablementService;
import it.prova.crm.enablement.EnablementEvent;
import it.prova.crm.purchase.PurchaseService;
import it.prova.crm.purchase.PurchaseService.CustomerSummary;
import it.prova.crm.purchase.ReceiptEvent;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Test diretti sui service (senza passare da Kafka) con MongoDB vero.
 */
class PurchaseServiceIntegrationTest extends IntegrationTestBase {

    private static final Instant T1 = Instant.parse("2026-03-10T09:00:00Z");
    private static final Instant T2 = Instant.parse("2026-03-11T09:00:00Z");

    @Autowired
    private PurchaseService purchaseService;

    @Autowired
    private CrmEnablementService enablementService;

    @Test
    void keepsCountTotalAndLastPurchase() {
        String org = enabledOrganization();

        purchaseService.onReceiptIssued(issued(1L, org, "mario", "10.00", T1));
        purchaseService.onReceiptIssued(issued(2L, org, "mario", "5.50", T2));

        CustomerSummary summary = purchaseService.summary(org, "mario");
        assertThat(summary.purchaseCount()).isEqualTo(2);
        assertThat(summary.totalSpent()).isEqualByComparingTo("15.50");
        assertThat(summary.lastPurchaseAt()).isEqualTo(T2);
    }

    @Test
    void duplicatedEventsAreCountedOnce() {
        String org = enabledOrganization();
        ReceiptEvent event = issued(1L, org, "mario", "10.00", T1);

        purchaseService.onReceiptIssued(event);
        purchaseService.onReceiptIssued(event);
        purchaseService.onReceiptIssued(event);

        assertThat(purchaseService.summary(org, "mario").purchaseCount()).isEqualTo(1);
    }

    @Test
    void cancellationRemovesThePurchase() {
        String org = enabledOrganization();
        purchaseService.onReceiptIssued(issued(1L, org, "mario", "10.00", T1));
        purchaseService.onReceiptIssued(issued(2L, org, "mario", "5.50", T2));

        purchaseService.onReceiptCancelled(cancelled(3L, 2L, org, "mario", T2));

        CustomerSummary summary = purchaseService.summary(org, "mario");
        assertThat(summary.purchaseCount()).isEqualTo(1);
        assertThat(summary.totalSpent()).isEqualByComparingTo("10.00");
        assertThat(summary.lastPurchaseAt()).isEqualTo(T1);
    }

    @Test
    void cancellationArrivedBeforeTheIssueIsHandled() {
        String org = enabledOrganization();

        // fuori ordine: prima l'annullo, poi l'emissione
        purchaseService.onReceiptCancelled(cancelled(3L, 1L, org, "mario", T2));
        purchaseService.onReceiptIssued(issued(1L, org, "mario", "10.00", T1));

        CustomerSummary summary = purchaseService.summary(org, "mario");
        assertThat(summary.purchaseCount()).isZero();
        assertThat(summary.totalSpent()).isEqualByComparingTo("0");
    }

    @Test
    void eventsOfOrganizationsNotEnabledToCrmAreIgnored() {
        String org = UUID.randomUUID().toString(); // nessuna abilitazione

        purchaseService.onReceiptIssued(issued(1L, org, "mario", "10.00", T1));

        assertThat(purchaseService.summary(org, "mario").purchaseCount()).isZero();
    }

    @Test
    void olderEnablementEventDoesNotOverwriteANewerOne() {
        String org = UUID.randomUUID().toString();

        // arriva prima la disabilitazione (più recente) e poi, in ritardo, l'abilitazione
        enablementService.onEnablementChanged(enablement(org, "DISABLED", T2));
        enablementService.onEnablementChanged(enablement(org, "ENABLED", T1));

        assertThat(enablementService.isEnabled(org, LocalDate.of(2026, 3, 12))).isFalse();
    }

    @Test
    void enablementForOtherProductsIsIgnored() {
        String org = UUID.randomUUID().toString();

        enablementService.onEnablementChanged(new EnablementEvent("e1", org, "SCONTRINA", "ENABLED",
                LocalDate.of(2026, 1, 1), null, T1));

        assertThat(enablementService.isEnabled(org, LocalDate.of(2026, 3, 12))).isFalse();
    }

    private String enabledOrganization() {
        String org = UUID.randomUUID().toString();
        enablementService.onEnablementChanged(enablement(org, "ENABLED", T1));
        return org;
    }

    private static EnablementEvent enablement(String org, String status, Instant occurredAt) {
        return new EnablementEvent(UUID.randomUUID().toString(), org, "CRM", status,
                LocalDate.of(2026, 1, 1), null, occurredAt);
    }

    private static ReceiptEvent issued(Long receiptId, String org, String customer, String total, Instant issuedAt) {
        return new ReceiptEvent(UUID.randomUUID().toString(), ReceiptEvent.ISSUED, receiptId, null, org, customer,
                new BigDecimal(total), issuedAt);
    }

    private static ReceiptEvent cancelled(Long receiptId, Long originalId, String org, String customer, Instant issuedAt) {
        return new ReceiptEvent(UUID.randomUUID().toString(), ReceiptEvent.CANCELLED, receiptId, originalId, org, customer,
                new BigDecimal("0"), issuedAt);
    }
}
