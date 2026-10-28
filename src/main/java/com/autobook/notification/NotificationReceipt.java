package com.autobook.notification;

import java.time.LocalDateTime;

/**
 * Response from the notification service.
 *
 * @param notificationId id the service assigned when it first delivered this idempotency key
 * @param idempotencyKey the key from the request
 * @param duplicate      {@code true} when the key had already been delivered and nothing was sent again
 * @param deliveredAt    when the service first delivered the notification
 */
public record NotificationReceipt(
        String notificationId,
        String idempotencyKey,
        boolean duplicate,
        LocalDateTime deliveredAt
) {
}
