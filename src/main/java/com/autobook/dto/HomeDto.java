package com.autobook.dto;

public record HomeDto(
        String applicationName,
        int providerCount,
        int serviceCount,
        int availableSlotCount
) {
}
