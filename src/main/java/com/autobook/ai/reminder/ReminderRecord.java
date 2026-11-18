package com.autobook.ai.reminder;

import java.time.LocalDateTime;

/**
 * A queued reminder joined with its outbox row, for the admin view.
 */
public record ReminderRecord(
        long reminderId,
        long appointmentId,
        String reminderType,
        String contentSource,
        String model,
        String triggerSource,
        LocalDateTime createdAt,
        long outboxId,
        String idempotencyKey,
        String deliveryStatus,
        int deliveryAttempts,
        String message
) {
}
