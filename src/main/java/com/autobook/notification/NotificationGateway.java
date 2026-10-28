package com.autobook.notification;

/**
 * AutoBook's single entry point to the external notification service (the distribution boundary).
 *
 * <p>The interface is deliberately coarse-grained: one call sends one complete
 * {@link NotificationRequest}. Callers never see HTTP, URLs, or JSON.
 */
public interface NotificationGateway {

    /**
     * Sends one notification. Makes exactly one remote attempt with a bounded timeout; retries and
     * fallback are the caller's job.
     *
     * @throws NotificationDeliveryException if the service is unreachable, too slow, or rejects the request
     */
    NotificationReceipt send(NotificationRequest request);
}
