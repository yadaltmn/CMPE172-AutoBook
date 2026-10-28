package com.autobook.notification;

import java.time.LocalDateTime;

/**
 * One row of {@code notification_outbox}.
 */
public record OutboxEntry(
        long outboxId,
        String idempotencyKey,
        long appointmentId,
        NotificationType type,
        String recipientEmail,
        String recipientName,
        String subject,
        String message,
        String confirmationNumber,
        OutboxStatus status,
        int attempts,
        int deliveryRounds,
        String lastError,
        String externalNotificationId,
        LocalDateTime createdAt,
        LocalDateTime nextAttemptAt,
        LocalDateTime sentAt
) {

    public NotificationRequest toRequest() {
        return new NotificationRequest(idempotencyKey, type, recipientEmail, recipientName, subject, message,
                appointmentId, confirmationNumber);
    }
}
