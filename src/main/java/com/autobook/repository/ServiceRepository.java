package com.autobook.repository;

import com.autobook.dto.ServiceDto;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
public class ServiceRepository {

    private static final RowMapper<ServiceDto> SERVICE_ROW_MAPPER = (resultSet, rowNum) -> new ServiceDto(
            resultSet.getLong("service_id"),
            resultSet.getString("name"),
            resultSet.getString("description"),
            resultSet.getInt("duration_minutes")
    );

    private final JdbcTemplate jdbcTemplate;

    public ServiceRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<ServiceDto> findAll() {
        return jdbcTemplate.query(
                "SELECT service_id, name, description, duration_minutes FROM services ORDER BY name",
                SERVICE_ROW_MAPPER
        );
    }

    public Optional<ServiceDto> findById(long serviceId) {
        return jdbcTemplate.query(
                "SELECT service_id, name, description, duration_minutes FROM services WHERE service_id = ?",
                SERVICE_ROW_MAPPER,
                serviceId
        ).stream().findFirst();
    }
}
