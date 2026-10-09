package com.autobook.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/**
 * Booking input from the customer. The customer is never part of the request; it is taken
 * from the authenticated session. {@code serviceId} is optional and, when sent, must match the slot.
 */
public record BookingRequest(
        @NotNull(message = "is required")
        @Positive(message = "must be a positive number")
        Long slotId,

        @Positive(message = "must be a positive number")
        Long serviceId
) {
}
