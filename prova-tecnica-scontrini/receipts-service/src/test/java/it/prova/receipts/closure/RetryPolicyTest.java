package it.prova.receipts.closure;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class RetryPolicyTest {

    private final RetryPolicy policy = new RetryPolicy(4, 30);

    @Test
    void delayDoublesAtEveryAttempt() {
        assertThat(policy.delayAfter(1)).isEqualTo(Duration.ofSeconds(30));
        assertThat(policy.delayAfter(2)).isEqualTo(Duration.ofSeconds(60));
        assertThat(policy.delayAfter(3)).isEqualTo(Duration.ofSeconds(120));
    }

    @Test
    void stopsAfterMaxAttempts() {
        assertThat(policy.canRetry(3)).isTrue();
        assertThat(policy.canRetry(4)).isFalse();
    }
}
