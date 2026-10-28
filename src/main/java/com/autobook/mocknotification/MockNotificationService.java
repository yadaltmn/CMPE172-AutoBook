package com.autobook.mocknotification;

import com.autobook.notification.NotificationReceipt;
import com.autobook.notification.NotificationRequest;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Stand-in for an external email/SMS provider. It "delivers" a notification by recording it in
 * memory. It is idempotent: a second request with an idempotency key it has already delivered is
 * acknowledged as a duplicate and is not delivered again.
 */
@Service
public class MockNotificationService {

    private static final Logger log = LoggerFactory.getLogger(MockNotificationService.class);

    private final Map<String, DeliveredNotification> deliveredByKey = new ConcurrentHashMap<>();
    private final AtomicLong receivedRequests = new AtomicLong();
    private final MockNotificationFaults faults;
    private final Clock clock;

    public MockNotificationService(MockNotificationFaults faults, Clock clock) {
        this.faults = faults;
        this.clock = clock;
    }

    public NotificationReceipt deliver(NotificationRequest request) {
        receivedRequests.incrementAndGet();
        if (faults.isUnavailable() || faults.consumeFailure()) {
            log.warn("Mock notification service rejected {} for appointment {}: simulated outage",
                    request.type(), request.appointmentId());
            throw new MockNotificationUnavailableException("Notification service is temporarily unavailable.");
        }
        simulateProcessingDelay();

        boolean[] duplicate = {true};
        DeliveredNotification delivered = deliveredByKey.computeIfAbsent(request.idempotencyKey(), key -> {
            duplicate[0] = false;
            return new DeliveredNotification(UUID.randomUUID().toString(), request, LocalDateTime.now(clock));
        });

        if (duplicate[0]) {
            log.info("Mock notification service ignored duplicate {} for appointment {} (key {})",
                    request.type(), request.appointmentId(), request.idempotencyKey());
        } else {
            log.info("Mock notification service delivered {} for appointment {} (key {}, id {})",
                    request.type(), request.appointmentId(), request.idempotencyKey(), delivered.notificationId());
        }
        return new NotificationReceipt(delivered.notificationId(), request.idempotencyKey(), duplicate[0],
                delivered.deliveredAt());
    }

    public List<DeliveredNotification> delivered() {
        return deliveredByKey.values().stream()
                .sorted(Comparator.comparing(DeliveredNotification::deliveredAt))
                .toList();
    }

    public boolean hasDelivered(String idempotencyKey) {
        return deliveredByKey.containsKey(idempotencyKey);
    }

    public long receivedRequests() {
        return receivedRequests.get();
    }

    /** Clears delivered notifications and request counts (used by tests and demos). */
    public void clear() {
        deliveredByKey.clear();
        receivedRequests.set(0);
    }

    private void simulateProcessingDelay() {
        long delay = faults.delayMillis();
        if (delay <= 0) {
            return;
        }
        try {
            Thread.sleep(delay);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new MockNotificationUnavailableException("Notification processing was interrupted.");
        }
    }

    public record DeliveredNotification(String notificationId, NotificationRequest request, LocalDateTime deliveredAt) {
    }
}
