package com.autobook.repository;

import com.autobook.dto.AvailabilitySlotDto;
import com.autobook.dto.ProviderSlotDto;
import com.autobook.dto.SlotDetailsDto;
import com.autobook.dto.SlotSearchCriteria;
import com.autobook.model.SlotLock;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
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
     * Pessimistic row lock: {@code SELECT ... FOR UPDATE} blocks any other transaction that tries to
     * lock the same slot until this transaction commits or rolls back.
     */
    public Optional<SlotLock> lockById(long slotId) {
        return jdbcTemplate.query("""
                SELECT slot_id, provider_id, service_id, start_time, end_time, is_available
                FROM availability_slots
                WHERE slot_id = ?
                FOR UPDATE
                """, (resultSet, rowNum) -> new SlotLock(
                resultSet.getLong("slot_id"),
                resultSet.getLong("provider_id"),
                resultSet.getLong("service_id"),
                resultSet.getTimestamp("start_time").toLocalDateTime(),
                resultSet.getTimestamp("end_time").toLocalDateTime(),
                resultSet.getBoolean("is_available")
        ), slotId).stream().findFirst();
    }

    /**
     * Atomic conditional update: flips the slot to unavailable only if it is still open and in the
     * future. Returns the number of rows changed, so 0 means another booking already claimed it.
     */
    public int claimIfAvailable(long slotId, LocalDateTime now) {
        return jdbcTemplate.update("""
                UPDATE availability_slots
                SET is_available = FALSE
                WHERE slot_id = ? AND is_available = TRUE AND start_time > ?
                """, slotId, Timestamp.valueOf(now));
    }

    public List<ProviderSlotDto> findByProviderId(long providerId, LocalDateTime now) {
        String sql = """
                SELECT
                    slot.slot_id,
                    service.service_id,
                    service.name AS service_name,
                    slot.start_time,
                    slot.end_time,
                    slot.is_available,
                    appointment.appointment_id,
                    appointment.status AS appointment_status,
                    customer.first_name || ' ' || customer.last_name AS customer_name,
                    customer.email AS customer_email,
                    (SELECT COUNT(*) FROM appointments history WHERE history.slot_id = slot.slot_id) AS history_count
                FROM availability_slots slot
                JOIN services service ON service.service_id = slot.service_id
                LEFT JOIN appointments appointment
                    ON appointment.slot_id = slot.slot_id AND appointment.status IN ('BOOKED', 'COMPLETED')
                LEFT JOIN users customer ON customer.user_id = appointment.user_id
                WHERE slot.provider_id = ?
                ORDER BY slot.start_time
                """;

        return jdbcTemplate.query(sql, (resultSet, rowNum) -> {
            Long appointmentId = resultSet.getObject("appointment_id", Long.class);
            String appointmentStatus = resultSet.getString("appointment_status");
            LocalDateTime start = resultSet.getTimestamp("start_time").toLocalDateTime();
            String state;
            if (appointmentStatus != null) {
                state = appointmentStatus.equals("COMPLETED") ? "COMPLETED" : "BOOKED";
            } else if (resultSet.getBoolean("is_available") && start.isAfter(now)) {
                state = "AVAILABLE";
            } else {
                state = "CLOSED";
            }
            return new ProviderSlotDto(
                    resultSet.getLong("slot_id"),
                    resultSet.getLong("service_id"),
                    resultSet.getString("service_name"),
                    start,
                    resultSet.getTimestamp("end_time").toLocalDateTime(),
                    state,
                    appointmentId,
                    resultSet.getString("customer_name"),
                    resultSet.getString("customer_email"),
                    resultSet.getLong("history_count") > 0
            );
        }, providerId);
    }

    /**
     * Counts this provider's slots whose time range overlaps [start, end).
     */
    public long countOverlapping(long providerId, LocalDateTime start, LocalDateTime end) {
        Long count = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM availability_slots
                WHERE provider_id = ? AND start_time < ? AND end_time > ?
                """, Long.class, providerId, Timestamp.valueOf(end), Timestamp.valueOf(start));
        return count == null ? 0 : count;
    }

    public long insert(long providerId, long serviceId, LocalDateTime start, LocalDateTime end) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement("""
                    INSERT INTO availability_slots (provider_id, service_id, start_time, end_time, is_available)
                    VALUES (?, ?, ?, ?, TRUE)
                    """, new String[] {"slot_id"});
            statement.setLong(1, providerId);
            statement.setLong(2, serviceId);
            statement.setTimestamp(3, Timestamp.valueOf(start));
            statement.setTimestamp(4, Timestamp.valueOf(end));
            return statement;
        }, keyHolder);
        return keyHolder.getKey().longValue();
    }

    public int delete(long slotId) {
        return jdbcTemplate.update("DELETE FROM availability_slots WHERE slot_id = ?", slotId);
    }

    public int close(long slotId) {
        return jdbcTemplate.update("UPDATE availability_slots SET is_available = FALSE WHERE slot_id = ?", slotId);
    }

    public long countAppointments(long slotId) {
        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM appointments WHERE slot_id = ?", Long.class, slotId);
        return count == null ? 0 : count;
    }

    /**
     * Returns a slot to the bookable pool after its appointment is cancelled.
     */
    public int reopen(long slotId) {
        return jdbcTemplate.update("UPDATE availability_slots SET is_available = TRUE WHERE slot_id = ?", slotId);
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
