package com.autobook.observability;

import com.autobook.notification.NotificationOutboxRepository;
import com.autobook.notification.OutboxStatus;
import org.springframework.boot.health.contributor.AbstractHealthIndicator;
import org.springframework.boot.health.contributor.Health;
import org.springframework.stereotype.Component;

/**
 * Reports the notification outbox under {@code /health} as component {@code notificationOutbox}.
 *
 * <p>It stays UP while notifications are waiting or have failed, because bookings keep working
 * without the notification service; the counts (visible to admins) show how much is waiting.
 * It is DOWN only if the outbox table cannot be read.
 */
@Component
public class NotificationOutboxHealthIndicator extends AbstractHealthIndicator {

    private final NotificationOutboxRepository outboxRepository;

    public NotificationOutboxHealthIndicator(NotificationOutboxRepository outboxRepository) {
        super("Notification outbox health check failed");
        this.outboxRepository = outboxRepository;
    }

    @Override
    protected void doHealthCheck(Health.Builder builder) {
        builder.up()
                .withDetail("pending", outboxRepository.countByStatus(OutboxStatus.PENDING))
                .withDetail("failed", outboxRepository.countByStatus(OutboxStatus.FAILED));
    }
}
