package com.autobook.model;

import java.time.LocalDateTime;

/**
 * Snapshot of an availability slot read with {@code SELECT ... FOR UPDATE}. While the surrounding
 * transaction is open no other transaction can lock or modify the same slot row.
 */
public record SlotLock(
        Long slotId,
        Long providerId,
        Long serviceId,
        LocalDateTime startTime,
        LocalDateTime endTime,
        boolean available
) {
}
