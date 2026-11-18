package com.autobook.ai.chat;

/**
 * An open appointment slot as the assistant shows it (no customer data).
 */
public record SlotOption(
        long slotId,
        String service,
        String provider,
        String start,
        String startIso,
        long durationMinutes
) {
}
