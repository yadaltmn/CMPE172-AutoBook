package com.autobook.dto;

import java.time.LocalDate;

public record SlotSearchCriteria(
        Long providerId,
        Long serviceId,
        LocalDate date,
        int page,
        int size
) {

    public static final int DEFAULT_PAGE_SIZE = 6;
    public static final int MAX_PAGE_SIZE = 50;

    public int offset() {
        return page * size;
    }
}
