package com.autobook.dto;

import com.autobook.model.AppointmentStatus;
import java.time.LocalDateTime;

public record AppointmentDto(
        Long appointmentId,
        Long slotId,
        Long customerId,
        String customerName,
        String customerEmail,
        Long providerId,
        String providerName,
        Long serviceId,
        String serviceName,
        LocalDateTime startTime,
        LocalDateTime endTime,
        AppointmentStatus status,
        LocalDateTime createdAt
) {
}
