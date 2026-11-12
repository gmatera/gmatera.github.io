package it.prova.receipts.receipt;

import it.prova.common.ApiException;
import it.prova.receipts.IntegrationTestBase;
import it.prova.receipts.receipt.ReceiptController.CreateReceiptRequest;
import it.prova.receipts.receipt.ReceiptController.LineRequest;
import it.prova.receipts.receipt.ReceiptService.IssueResult;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;

class ReceiptServiceIntegrationTest extends IntegrationTestBase {

    @Autowired
    private ReceiptService receiptService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void issuesReceiptWithTotalsAndFirstNumber() {
        String org = newId();

        IssueResult result = receiptService.issue(newId(), request(org, "CASSA-1", "cliente-1"));

        Receipt receipt = result.receipt();
        assertThat(result.created()).isTrue();
        assertThat(receipt.getNumber()).isEqualTo(1);
        assertThat(receipt.getType()).isEqualTo(ReceiptType.SALE);
        assertThat(receipt.getTotalAmount()).isEqualByComparingTo("14.40"); // 2 x 1.10 + 12.20
        assertThat(receipt.getVatAmount()).isEqualByComparingTo("2.40");
        assertThat(countOutboxEvents(org)).isEqualTo(1);
    }

    @Test
    void concurrentRequestsGetConsecutiveNumbersWithoutGapsOrDuplicates() throws Exception {
        String org = newId();
        int requests = 30;
        List<Callable<Integer>> tasks = new ArrayList<>();
        for (int i = 0; i < requests; i++) {
            tasks.add(() -> receiptService.issue(newId(), request(org, "CASSA-1", null)).receipt().getNumber());
        }

        List<Integer> numbers = runConcurrently(tasks);

        assertThat(numbers).containsExactlyInAnyOrderElementsOf(IntStream.rangeClosed(1, requests).boxed().toList());
    }

    @Test
    void cashRegistersHaveIndependentNumbering() {
        String org = newId();

        receiptService.issue(newId(), request(org, "CASSA-1", null));
        receiptService.issue(newId(), request(org, "CASSA-1", null));
        Receipt other = receiptService.issue(newId(), request(org, "CASSA-2", null)).receipt();

        assertThat(other.getNumber()).isEqualTo(1);
    }

    @Test
    void sameIdempotencyKeyReturnsTheSameReceipt() {
        String org = newId();
        String key = newId();

        IssueResult first = receiptService.issue(key, request(org, "CASSA-1", null));
        IssueResult second = receiptService.issue(key, request(org, "CASSA-1", null));

        assertThat(second.created()).isFalse();
        assertThat(second.receipt().getId()).isEqualTo(first.receipt().getId());
        assertThat(countReceipts(org)).isEqualTo(1);
    }

    @Test
    void sameIdempotencyKeyInParallelEmitsOnlyOneReceiptAndLeavesNoGap() throws Exception {
        String org = newId();
        String key = newId();
        List<Callable<Long>> tasks = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            tasks.add(() -> receiptService.issue(key, request(org, "CASSA-1", null)).receipt().getId());
        }

        List<Long> ids = runConcurrently(tasks);

        assertThat(ids).containsOnly(ids.get(0));
        assertThat(countReceipts(org)).isEqualTo(1);
        assertThat(countOutboxEvents(org)).isEqualTo(1);
        // le richieste "perdenti" hanno fatto rollback anche del contatore: il prossimo numero è 2
        assertThat(receiptService.issue(newId(), request(org, "CASSA-1", null)).receipt().getNumber()).isEqualTo(2);
    }

    @Test
    void organizationNotEnabledCannotIssue() {
        String org = newId();
        doThrow(new ApiException(HttpStatus.FORBIDDEN, "PRODUCT_NOT_ENABLED", "non abilitata"))
                .when(accountClient).checkCanIssue(anyString());

        assertThatThrownBy(() -> receiptService.issue(newId(), request(org, "CASSA-1", null)))
                .isInstanceOf(ApiException.class)
                .hasFieldOrPropertyWithValue("code", "PRODUCT_NOT_ENABLED");
        assertThat(countReceipts(org)).isZero();
    }

    @Test
    void invalidVatRateIsRejected() {
        CreateReceiptRequest request = new CreateReceiptRequest(newId(), "CASSA-1", PaymentType.CASH, null,
                List.of(new LineRequest("pane", 1, new BigDecimal("2.00"), 5)));

        assertThatThrownBy(() -> receiptService.issue(newId(), request))
                .isInstanceOf(ApiException.class)
                .hasFieldOrPropertyWithValue("code", "INVALID_VAT_RATE");
    }

    @Test
    void cancellationCreatesLinkedDocumentAndKeepsTheOriginal() {
        String org = newId();
        Receipt original = receiptService.issue(newId(), request(org, "CASSA-1", "cliente-1")).receipt();

        Receipt cancellation = receiptService.cancel(original.getId());

        assertThat(cancellation.getType()).isEqualTo(ReceiptType.CANCELLATION);
        assertThat(cancellation.getOriginalReceiptId()).isEqualTo(original.getId());
        assertThat(cancellation.getNumber()).isEqualTo(2);
        assertThat(cancellation.getTotalAmount()).isEqualByComparingTo(original.getTotalAmount());
        assertThat(cancellation.getCustomerId()).isEqualTo("cliente-1");
        // l'originale c'è ancora
        assertThat(receiptService.get(original.getId()).getType()).isEqualTo(ReceiptType.SALE);
        assertThat(countOutboxEvents(org)).isEqualTo(2);
    }

    @Test
    void receiptCannotBeCancelledTwice() {
        Receipt original = receiptService.issue(newId(), request(newId(), "CASSA-1", null)).receipt();
        receiptService.cancel(original.getId());

        assertThatThrownBy(() -> receiptService.cancel(original.getId()))
                .isInstanceOf(ApiException.class)
                .hasFieldOrPropertyWithValue("code", "RECEIPT_ALREADY_CANCELLED");
    }

    @Test
    void concurrentCancellationsCreateOnlyOneDocument() throws Exception {
        String org = newId();
        Receipt original = receiptService.issue(newId(), request(org, "CASSA-1", null)).receipt();
        List<Callable<String>> tasks = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            tasks.add(() -> {
                try {
                    receiptService.cancel(original.getId());
                    return "OK";
                } catch (ApiException e) {
                    return e.getCode();
                }
            });
        }

        List<String> results = runConcurrently(tasks);

        assertThat(results).containsOnlyOnce("OK");
        assertThat(results).filteredOn(r -> !r.equals("OK")).containsOnly("RECEIPT_ALREADY_CANCELLED");
        assertThat(countReceipts(org)).isEqualTo(2);
    }

    private CreateReceiptRequest request(String org, String cashRegister, String customer) {
        return new CreateReceiptRequest(org, cashRegister, PaymentType.CARD, customer, List.of(
                new LineRequest("caffè", 2, new BigDecimal("1.10"), 10),
                new LineRequest("panino", 1, new BigDecimal("12.20"), 22)));
    }

    private <T> List<T> runConcurrently(List<Callable<T>> tasks) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(tasks.size());
        try {
            List<T> results = new ArrayList<>();
            for (Future<T> future : executor.invokeAll(tasks)) {
                results.add(future.get());
            }
            return results;
        } finally {
            executor.shutdown();
        }
    }

    private int countReceipts(String org) {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM receipt WHERE organization_id = ?", Integer.class, org);
    }

    private int countOutboxEvents(String org) {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM outbox_event WHERE message_key = ?", Integer.class, org);
    }

    private static String newId() {
        return UUID.randomUUID().toString();
    }
}
