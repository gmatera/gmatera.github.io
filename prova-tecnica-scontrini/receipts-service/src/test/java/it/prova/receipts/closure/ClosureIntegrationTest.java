package it.prova.receipts.closure;

import it.prova.common.ApiException;
import it.prova.receipts.IntegrationTestBase;
import it.prova.receipts.receipt.PaymentType;
import it.prova.receipts.receipt.Receipt;
import it.prova.receipts.receipt.ReceiptLine;
import it.prova.receipts.receipt.ReceiptRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.client.ResourceAccessException;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ClosureIntegrationTest extends IntegrationTestBase {

    private static final LocalDate YESTERDAY = LocalDate.now(ZoneId.of("Europe/Rome")).minusDays(1);

    @Autowired
    private ClosureService closureService;

    @Autowired
    private TransmissionJob transmissionJob;

    @Autowired
    private DailyClosureRepository closureRepository;

    @Autowired
    private ReceiptRepository receiptRepository;

    @Test
    void closureGroupsTheReceiptsOfTheDay() {
        String org = newId();
        saveReceiptOfYesterday(org, "CASSA-1", 1, "10.00");
        saveReceiptOfYesterday(org, "CASSA-1", 2, "5.50");
        saveReceiptOfYesterday(org, "CASSA-2", 1, "99.00"); // altro punto cassa, non deve entrare

        DailyClosure closure = closureService.create(org, "CASSA-1", YESTERDAY);

        assertThat(closure.getReceiptCount()).isEqualTo(2);
        assertThat(closure.getTotalAmount()).isEqualByComparingTo("15.50");
        assertThat(closure.getStatus()).isEqualTo(ClosureStatus.TO_SEND);
    }

    @Test
    void creatingTheSameClosureTwiceReturnsTheExistingOne() {
        String org = newId();
        saveReceiptOfYesterday(org, "CASSA-1", 1, "10.00");

        DailyClosure first = closureService.create(org, "CASSA-1", YESTERDAY);
        DailyClosure second = closureService.create(org, "CASSA-1", YESTERDAY);

        assertThat(second.getId()).isEqualTo(first.getId());
    }

    @Test
    void nightlyJobCreatesOneClosurePerCashRegisterAndCanRunTwice() {
        String org = newId();
        saveReceiptOfYesterday(org, "CASSA-1", 1, "10.00");
        saveReceiptOfYesterday(org, "CASSA-2", 1, "20.00");

        closureService.createYesterdayClosures();
        closureService.createYesterdayClosures(); // seconda esecuzione: non deve creare doppioni

        assertThat(closureService.findByOrganization(org))
                .extracting(DailyClosure::getCashRegisterId)
                .containsExactlyInAnyOrder("CASSA-1", "CASSA-2");
    }

    @Test
    void closureOfTodayIsNotAllowed() {
        assertThatThrownBy(() -> closureService.create(newId(), "CASSA-1", YESTERDAY.plusDays(1)))
                .isInstanceOf(ApiException.class)
                .hasFieldOrPropertyWithValue("code", "CLOSURE_DATE_NOT_PAST");
    }

    @Test
    void successfulTransmissionMarksClosureAsSent() {
        DailyClosure closure = newClosure();
        when(transmissionClient.send(any())).thenReturn("PROT-1");

        transmissionJob.transmit(closure.getId());

        DailyClosure saved = closureRepository.findById(closure.getId()).orElseThrow();
        assertThat(saved.getStatus()).isEqualTo(ClosureStatus.SENT);
        assertThat(saved.getProtocolNumber()).isEqualTo("PROT-1");
        assertThat(saved.getAttempts()).isEqualTo(1);
    }

    @Test
    void sameClosureIsNeverTransmittedTwiceEvenIfTheJobRunsInParallel() throws Exception {
        DailyClosure closure = newClosure();
        when(transmissionClient.send(any())).thenAnswer(invocation -> {
            Thread.sleep(300); // simulo un ente lento
            return "PROT-2";
        });

        // 5 "istanze" del job che partono nello stesso momento sulla stessa chiusura
        int instances = 5;
        ExecutorService executor = Executors.newFixedThreadPool(instances);
        CountDownLatch start = new CountDownLatch(1);
        for (int i = 0; i < instances; i++) {
            executor.submit(() -> {
                start.await();
                transmissionJob.transmit(closure.getId());
                return null;
            });
        }
        start.countDown();
        executor.shutdown();
        assertThat(executor.awaitTermination(30, TimeUnit.SECONDS)).isTrue();

        verify(transmissionClient, times(1)).send(any());
        assertThat(closureRepository.findById(closure.getId()).orElseThrow().getStatus()).isEqualTo(ClosureStatus.SENT);
    }

    @Test
    void failedTransmissionIsRetriedAndThenMarkedAsFailed() {
        DailyClosure closure = newClosure();
        when(transmissionClient.send(any())).thenThrow(new ResourceAccessException("timeout"));

        transmissionJob.transmit(closure.getId());
        DailyClosure afterFirst = closureRepository.findById(closure.getId()).orElseThrow();
        assertThat(afterFirst.getStatus()).isEqualTo(ClosureStatus.TO_SEND);
        assertThat(afterFirst.getAttempts()).isEqualTo(1);
        assertThat(afterFirst.getLastError()).contains("timeout");

        // nei test max-attempts = 3
        transmissionJob.transmit(closure.getId());
        transmissionJob.transmit(closure.getId());

        DailyClosure afterLast = closureRepository.findById(closure.getId()).orElseThrow();
        assertThat(afterLast.getStatus()).isEqualTo(ClosureStatus.FAILED);
        assertThat(afterLast.getAttempts()).isEqualTo(3);

        // una chiusura fallita non viene più ripresa dal job
        transmissionJob.transmit(closure.getId());
        verify(transmissionClient, times(3)).send(any());
    }

    private DailyClosure newClosure() {
        String org = newId();
        saveReceiptOfYesterday(org, "CASSA-1", 1, "10.00");
        return closureService.create(org, "CASSA-1", YESTERDAY);
    }

    private void saveReceiptOfYesterday(String org, String cashRegister, int number, String price) {
        ReceiptLine line = new ReceiptLine("articolo", 1, new BigDecimal(price), 22);
        receiptRepository.save(Receipt.sale(org, cashRegister, number, PaymentType.CASH, null, List.of(line),
                newId(), Instant.now(), YESTERDAY));
    }

    private static String newId() {
        return UUID.randomUUID().toString();
    }
}
