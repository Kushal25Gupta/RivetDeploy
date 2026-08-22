package com.rivetdeploy.backend.scheduler;

import com.rivetdeploy.backend.deployment.FailureType;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.ThreadLocalRandom;

@Component
public class RetryPolicy {

    private final int maxAttempts;
    private final Duration initialDelay;
    private final Duration maxDelay;
    private final double backoffMultiplier;

    public RetryPolicy(
            @Value("${rivetdeploy.retry.max-attempts:3}") int maxAttempts,
            @Value("${rivetdeploy.retry.initial-delay-ms:1000}") long initialDelayMs,
            @Value("${rivetdeploy.retry.max-delay-ms:30000}") long maxDelayMs,
            @Value("${rivetdeploy.retry.multiplier:2.0}") double backoffMultiplier) {
        this.maxAttempts = maxAttempts;
        this.initialDelay = Duration.ofMillis(initialDelayMs);
        this.maxDelay = Duration.ofMillis(maxDelayMs);
        this.backoffMultiplier = backoffMultiplier;
    }

    public boolean shouldRetry(FailureType failureType, int currentAttempt) {
        if (!failureType.isRetryable()) {
            return false;
        }
        return currentAttempt < maxAttempts;
    }

    public Duration calculateBackoff(int attempt) {
        if (attempt <= 0) {
            return Duration.ZERO;
        }
        
        long delayMillis = (long) (initialDelay.toMillis() * Math.pow(backoffMultiplier, attempt - 1));
        long cappedDelay = Math.min(delayMillis, maxDelay.toMillis());
        
        // Add +/- 20% jitter
        long jitter = (long) (ThreadLocalRandom.current().nextDouble(-0.2, 0.2) * cappedDelay);
        long finalDelay = Math.max(0, cappedDelay + jitter);

        return Duration.ofMillis(finalDelay);
    }

    public int getMaxAttempts() {
        return maxAttempts;
    }
}
