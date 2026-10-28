package com.autobook.notification;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Coarse-grained message sent across the distribution boundary to the notification service.
 *
 * <p>One request carries everything the service needs to notify the customer (recipient, type,
 * subject, and the finished message text), so a notification is a single remote call instead of
 * several chatty calls that would each need their own timeout and retry handling.
 *
 * <p>{@code idempotencyKey} identifies the business event (for example
 * {@code BOOKING_CONFIRMED-appointment-42}). The notification service delivers each key at most
 * once, so AutoBook can safely retry a request whose response was lost.
 */
public record NotificationRequest(
        @NotBlank @Size(max = 100) String idempotencyKey,
        @NotNull NotificationType type,
        @NotBlank @Email @Size(max = 255) String recipientEmail,
        @NotBlank @Size(max = 200) String recipientName,
        @NotBlank @Size(max = 200) String subject,
        @NotBlank @Size(max = 2000) String message,
        @NotNull Long appointmentId,
        @NotBlank @Size(max = 20) String confirmationNumber
) {
}
