package com.autobook.ai.chat;

/**
 * One of the signed-in customer's appointments as the assistant shows it.
 */
public record AppointmentOption(
        long appointmentId,
        String confirmationNumber,
        String service,
        String provider,
        String start,
        String startIso,
        String status,
        boolean cancellable
) {
}
