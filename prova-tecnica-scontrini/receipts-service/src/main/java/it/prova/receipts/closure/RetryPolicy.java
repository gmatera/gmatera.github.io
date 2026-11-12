package it.prova.receipts.closure;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Backoff esponenziale: dopo il tentativo n aspetto baseDelay * 2^(n-1)
 * (con base 30s: 30s, 1m, 2m, 4m...) fino a maxAttempts tentativi.
 */
@Component
public class RetryPolicy {

    private final int maxAttempts;
    private final Duration baseDelay;

    public RetryPolicy(@Value("${transmission.retry.max-attempts}") int maxAttempts,
                       @Value("${transmission.retry.base-delay-seconds}") long baseDelaySeconds) {
        this.maxAttempts = maxAttempts;
        this.baseDelay = Duration.ofSeconds(baseDelaySeconds);
    }

    public boolean canRetry(int attemptsDone) {
        return attemptsDone < maxAttempts;
    }

    public Duration delayAfter(int attemptsDone) {
        return baseDelay.multipliedBy(1L << (attemptsDone - 1));
    }
}
