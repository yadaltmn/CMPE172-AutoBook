package com.autobook.observability;

import com.autobook.notification.NotificationOutboxRepository;
import com.autobook.notification.OutboxStatus;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

/**
 * Notification metrics:
 * <ul>
 *     <li>{@code autobook.notifications.rounds}: delivery rounds, tagged {@code outcome} =
 *     sent / retry_scheduled / failed</li>
 *     <li>{@code autobook.notifications.retries}: HTTP attempts that were retried after a failure</li>
 *     <li>{@code autobook.notifications.outbox}: current outbox rows, tagged {@code status} = pending / failed</li>
 * </ul>
 */
@Component
public class NotificationMetrics {

    private final MeterRegistry registry;
    private final Counter retries;

    public NotificationMetrics(MeterRegistry registry, NotificationOutboxRepository outboxRepository) {
        this.registry = registry;
        this.retries = Counter.builder("autobook.notifications.retries")
                .description("Notification attempts retried after a timeout or error").register(registry);
        registerOutboxGauge(outboxRepository, OutboxStatus.PENDING);
        registerOutboxGauge(outboxRepository, OutboxStatus.FAILED);
    }

    public void retried() {
        retries.increment();
    }

    public void roundFinished(String outcome) {
        Counter.builder("autobook.notifications.rounds")
                .description("Notification delivery rounds by outcome")
                .tag("outcome", outcome)
                .register(registry)
                .increment();
    }

    private void registerOutboxGauge(NotificationOutboxRepository outboxRepository, OutboxStatus status) {
        Gauge.builder("autobook.notifications.outbox", outboxRepository, repository -> {
                    try {
                        return repository.countByStatus(status);
                    } catch (RuntimeException ex) {
                        return Double.NaN;
                    }
                })
                .description("Notifications in the outbox by status")
                .tag("status", status.name().toLowerCase())
                .register(registry);
    }
}
