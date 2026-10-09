package com.autobook.dto;

import java.util.List;

/**
 * A customer's appointments split into upcoming bookings and everything else
 * (past, cancelled, or completed appointments).
 */
public record AppointmentHistoryDto(
        List<AppointmentDto> upcoming,
        List<AppointmentDto> history
) {
}
