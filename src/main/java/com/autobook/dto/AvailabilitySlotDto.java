package com.autobook.dto;

import java.time.LocalDateTime;

public record AvailabilitySlotDto(
        Long slotId,
        Long providerId,
        String providerName,
        Long serviceId,
        String serviceName,
        LocalDateTime startTime,
        LocalDateTime endTime,
        boolean available
) {
}
