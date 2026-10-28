package com.autobook.notification;

/**
 * Published inside the booking or cancellation transaction after the outbox row is written.
 * {@link NotificationDispatcher} only reacts after that transaction commits.
 */
public record NotificationEnqueuedEvent(long outboxId) {
}
