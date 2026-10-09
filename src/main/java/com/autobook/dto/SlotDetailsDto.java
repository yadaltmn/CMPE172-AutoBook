package com.autobook.dto;

import java.time.LocalDateTime;

public record SlotDetailsDto(
        Long slotId,
        Long providerId,
        String providerName,
        String providerPhone,
        Long serviceId,
        String serviceName,
        String serviceDescription,
        int durationMinutes,
        LocalDateTime startTime,
        LocalDateTime endTime,
        boolean available
) {
}
