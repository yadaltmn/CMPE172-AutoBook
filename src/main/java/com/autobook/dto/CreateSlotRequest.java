package com.autobook.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.time.LocalDateTime;
import org.springframework.format.annotation.DateTimeFormat;

public record CreateSlotRequest(
        @NotNull(message = "is required")
        @Positive(message = "must be a positive number")
        Long serviceId,

        @NotNull(message = "is required")
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
        LocalDateTime startTime,

        @NotNull(message = "is required")
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
        LocalDateTime endTime
) {
}
