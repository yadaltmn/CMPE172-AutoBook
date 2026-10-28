package com.autobook.notification;

import java.time.Duration;
import com.autobook.observability.NotificationMetrics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Bounded retries with exponential backoff around {@link NotificationGateway}.
 *
 * <p>Each attempt is limited by the gateway's timeout, and the number of attempts is capped, so a
 * delivery round always finishes in bounded time. Retrying is safe because every attempt sends the
 * same idempotency key: if an earlier attempt reached the service but its response was lost, the
 * service answers "duplicate" instead of notifying the customer twice.
 */
@Component
public class ResilientNotificationSender {

    private static final Logger log = LoggerFactory.getLogger(ResilientNotificationSender.class);

    private final NotificationGateway gateway;
    private final NotificationProperties properties;
    private final NotificationMetrics metrics;

    public ResilientNotificationSender(NotificationGateway gateway, NotificationProperties properties,
                                       NotificationMetrics metrics) {
        this.gateway = gateway;
        this.properties = properties;
        this.metrics = metrics;
    }

    public DeliveryResult send(NotificationRequest request) {
        int maxAttempts = Math.max(1, properties.maxAttempts());
        Duration backoff = properties.initialBackoff();

        for (int attempt = 1; ; attempt++) {
            try {
                NotificationReceipt receipt = gateway.send(request);
                if (attempt > 1) {
                    log.info("Notification {} delivered on attempt {}/{}", request.idempotencyKey(), attempt, maxAttempts);
                }
                return new DeliveryResult(receipt, attempt);
            } catch (NotificationDeliveryException ex) {
                if (!ex.isRetryable() || attempt >= maxAttempts) {
                    throw new DeliveryFailedException(ex.getMessage(), ex.isRetryable(), attempt, ex);
                }
                log.warn("Notification {} attempt {}/{} failed: {} Retrying in {} ms",
                        request.idempotencyKey(), attempt, maxAttempts, ex.getMessage(), backoff.toMillis());
                metrics.retried();
                sleep(backoff);
                backoff = nextBackoff(backoff);
            }
        }
    }

    private Duration nextBackoff(Duration current) {
        long next = (long) (current.toMillis() * properties.backoffMultiplier());
        return Duration.ofMillis(Math.min(next, properties.maxBackoff().toMillis()));
    }

    private static void sleep(Duration duration) {
        try {
            Thread.sleep(duration.toMillis());
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new DeliveryFailedException("Interrupted while waiting to retry.", true, 0, ex);
        }
    }

    /** A delivered (or duplicate-acknowledged) notification and how many HTTP attempts it took. */
    public record DeliveryResult(NotificationReceipt receipt, int attempts) {
    }

    /** All attempts of one round failed, or the request was rejected as invalid. */
    public static class DeliveryFailedException extends RuntimeException {

        private final boolean retryable;
        private final int attempts;

        DeliveryFailedException(String message, boolean retryable, int attempts, Throwable cause) {
            super(message, cause);
            this.retryable = retryable;
            this.attempts = attempts;
        }

        public boolean isRetryable() {
            return retryable;
        }

        public int attempts() {
            return attempts;
        }
    }
}
