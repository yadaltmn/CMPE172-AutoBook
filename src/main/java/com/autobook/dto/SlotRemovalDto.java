package com.autobook.dto;

/**
 * Result of removing a slot. {@code DELETED} means the row was removed; {@code CLOSED} means the
 * slot has cancelled-appointment history, so it was kept for the record but can no longer be booked.
 */
public record SlotRemovalDto(
        Long slotId,
        String outcome,
        String message
) {
}
