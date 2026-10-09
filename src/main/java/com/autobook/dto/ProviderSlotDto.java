package com.autobook.dto;

import java.time.LocalDateTime;

/**
 * A provider's view of one of their slots, including who booked it (if anyone).
 * {@code state} is one of AVAILABLE, BOOKED, COMPLETED, or CLOSED.
 */
public record ProviderSlotDto(
        Long slotId,
        Long serviceId,
        String serviceName,
        LocalDateTime startTime,
        LocalDateTime endTime,
        String state,
        Long appointmentId,
        String customerName,
        String customerEmail,
        boolean hasAppointmentHistory
) {

    /**
     * Only open slots can be removed; booked and completed slots must stay for the customer.
     */
    public boolean removable() {
        return "AVAILABLE".equals(state);
    }
}
