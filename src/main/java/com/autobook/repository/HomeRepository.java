package com.autobook.repository;

import com.autobook.dto.HomeDto;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class HomeRepository {

    private final JdbcTemplate jdbcTemplate;

    public HomeRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public HomeDto getHomeSummary() {
        Integer providerCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM providers", Integer.class);
        Integer serviceCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM services", Integer.class);
        Integer availableSlotCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM availability_slots WHERE is_available = TRUE",
                Integer.class
        );

        return new HomeDto(
                "AutoBook",
                valueOrZero(providerCount),
                valueOrZero(serviceCount),
                valueOrZero(availableSlotCount)
        );
    }

    private int valueOrZero(Integer value) {
        return value == null ? 0 : value;
    }
}
