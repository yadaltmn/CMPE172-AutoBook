package com.autobook.dto;

public record ServiceDto(
        Long serviceId,
        String name,
        String description,
        int durationMinutes
) {
}
