package com.autobook.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.time.LocalDateTime;

public record CreateSlotRequest(
        @NotNull(message = "is required")
        @Positive(message = "must be a positive number")
        Long serviceId,

        @NotNull(message = "is required")
        LocalDateTime startTime,

        @NotNull(message = "is required")
        LocalDateTime endTime
) {
}
