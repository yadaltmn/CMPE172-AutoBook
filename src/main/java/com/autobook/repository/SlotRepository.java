package com.autobook.repository;

import com.autobook.dto.AvailabilitySlotDto;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
public class SlotRepository {

    private final JdbcTemplate jdbcTemplate;
    private final RowMapper<AvailabilitySlotDto> slotRowMapper = new AvailabilitySlotRowMapper();

    public SlotRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<AvailabilitySlotDto> findAvailableSlots() {
        String sql = """
                SELECT
                    slot.slot_id,
                    provider.provider_id,
                    provider.name AS provider_name,
                    service.service_id,
                    service.name AS service_name,
                    slot.start_time,
                    slot.end_time,
                    slot.is_available
                FROM availability_slots slot
                JOIN providers provider ON provider.provider_id = slot.provider_id
                JOIN services service ON service.service_id = slot.service_id
                WHERE slot.is_available = TRUE
                ORDER BY slot.start_time, provider.name, service.name
                """;

        return jdbcTemplate.query(sql, slotRowMapper);
    }

    private static class AvailabilitySlotRowMapper implements RowMapper<AvailabilitySlotDto> {
        @Override
        public AvailabilitySlotDto mapRow(ResultSet resultSet, int rowNum) throws SQLException {
            return new AvailabilitySlotDto(
                    resultSet.getLong("slot_id"),
                    resultSet.getLong("provider_id"),
                    resultSet.getString("provider_name"),
                    resultSet.getLong("service_id"),
                    resultSet.getString("service_name"),
                    resultSet.getTimestamp("start_time").toLocalDateTime(),
                    resultSet.getTimestamp("end_time").toLocalDateTime(),
                    resultSet.getBoolean("is_available")
            );
        }
    }
}
