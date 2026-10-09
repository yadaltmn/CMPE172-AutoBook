package com.autobook.repository;

import com.autobook.dto.AvailabilitySlotDto;
import com.autobook.dto.SlotDetailsDto;
import com.autobook.dto.SlotSearchCriteria;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
public class SlotRepository {

    private static final String SLOT_COLUMNS = """
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
                """;

    private final JdbcTemplate jdbcTemplate;
    private final RowMapper<AvailabilitySlotDto> slotRowMapper = new AvailabilitySlotRowMapper();

    public SlotRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * Milestone 1 listing used by {@code GET /slots}: every open slot that has not started yet.
     */
    public List<AvailabilitySlotDto> findAvailableSlots(LocalDateTime now) {
        String sql = SLOT_COLUMNS + """
                WHERE slot.is_available = TRUE
                  AND slot.start_time > ?
                ORDER BY slot.start_time, provider.name, service.name
                """;

        return jdbcTemplate.query(sql, slotRowMapper, Timestamp.valueOf(now));
    }

    public List<AvailabilitySlotDto> searchAvailableSlots(SlotSearchCriteria criteria, LocalDateTime now) {
        List<Object> parameters = new ArrayList<>();
        String sql = SLOT_COLUMNS
                + availableSlotFilter(criteria, now, parameters)
                + " ORDER BY slot.start_time, provider.name, service.name, slot.slot_id LIMIT ? OFFSET ?";
        parameters.add(criteria.size());
        parameters.add(criteria.offset());

        return jdbcTemplate.query(sql, slotRowMapper, parameters.toArray());
    }

    public long countAvailableSlots(SlotSearchCriteria criteria, LocalDateTime now) {
        List<Object> parameters = new ArrayList<>();
        String sql = "SELECT COUNT(*) FROM availability_slots slot "
                + availableSlotFilter(criteria, now, parameters);
        Long count = jdbcTemplate.queryForObject(sql, Long.class, parameters.toArray());
        return count == null ? 0 : count;
    }

    public Optional<SlotDetailsDto> findDetailsById(long slotId) {
        String sql = """
                SELECT
                    slot.slot_id,
                    provider.provider_id,
                    provider.name AS provider_name,
                    provider.phone AS provider_phone,
                    service.service_id,
                    service.name AS service_name,
                    service.description AS service_description,
                    service.duration_minutes,
                    slot.start_time,
                    slot.end_time,
                    slot.is_available
                FROM availability_slots slot
                JOIN providers provider ON provider.provider_id = slot.provider_id
                JOIN services service ON service.service_id = slot.service_id
                WHERE slot.slot_id = ?
                """;

        return jdbcTemplate.query(sql, (resultSet, rowNum) -> new SlotDetailsDto(
                resultSet.getLong("slot_id"),
                resultSet.getLong("provider_id"),
                resultSet.getString("provider_name"),
                resultSet.getString("provider_phone"),
                resultSet.getLong("service_id"),
                resultSet.getString("service_name"),
                resultSet.getString("service_description"),
                resultSet.getInt("duration_minutes"),
                resultSet.getTimestamp("start_time").toLocalDateTime(),
                resultSet.getTimestamp("end_time").toLocalDateTime(),
                resultSet.getBoolean("is_available")
        ), slotId).stream().findFirst();
    }

    /**
     * Builds the shared WHERE clause for searching and counting. Every value is bound as a
     * JDBC parameter; only fixed SQL fragments are concatenated.
     */
    private String availableSlotFilter(SlotSearchCriteria criteria, LocalDateTime now, List<Object> parameters) {
        StringBuilder where = new StringBuilder(" WHERE slot.is_available = TRUE AND slot.start_time > ?");
        parameters.add(Timestamp.valueOf(now));

        if (criteria.providerId() != null) {
            where.append(" AND slot.provider_id = ?");
            parameters.add(criteria.providerId());
        }
        if (criteria.serviceId() != null) {
            where.append(" AND slot.service_id = ?");
            parameters.add(criteria.serviceId());
        }
        if (criteria.date() != null) {
            where.append(" AND slot.start_time >= ? AND slot.start_time < ?");
            parameters.add(Timestamp.valueOf(criteria.date().atStartOfDay()));
            parameters.add(Timestamp.valueOf(criteria.date().plusDays(1).atStartOfDay()));
        }
        return where.toString();
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
