package com.autobook.dto;

public record ProviderDto(
        Long providerId,
        Long userId,
        String name,
        String email,
        String phone
) {
}
