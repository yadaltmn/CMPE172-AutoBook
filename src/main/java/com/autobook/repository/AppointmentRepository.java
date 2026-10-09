package com.autobook.repository;

import com.autobook.dto.AppointmentDto;
import com.autobook.model.AppointmentStatus;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

@Repository
public class AppointmentRepository {

    private static final String SELECT_APPOINTMENT = """
            SELECT
                appointment.appointment_id,
                appointment.slot_id,
                customer.user_id AS customer_id,
                customer.first_name || ' ' || customer.last_name AS customer_name,
                customer.email AS customer_email,
                provider.provider_id,
                provider.name AS provider_name,
                service.service_id,
                service.name AS service_name,
                slot.start_time,
                slot.end_time,
                appointment.status,
                appointment.created_at
            FROM appointments appointment
            JOIN users customer ON customer.user_id = appointment.user_id
            JOIN providers provider ON provider.provider_id = appointment.provider_id
            JOIN services service ON service.service_id = appointment.service_id
            JOIN availability_slots slot ON slot.slot_id = appointment.slot_id
            """;

    private static final RowMapper<AppointmentDto> APPOINTMENT_ROW_MAPPER = (resultSet, rowNum) -> new AppointmentDto(
            resultSet.getLong("appointment_id"),
            resultSet.getLong("slot_id"),
            resultSet.getLong("customer_id"),
            resultSet.getString("customer_name"),
            resultSet.getString("customer_email"),
            resultSet.getLong("provider_id"),
            resultSet.getString("provider_name"),
            resultSet.getLong("service_id"),
            resultSet.getString("service_name"),
            resultSet.getTimestamp("start_time").toLocalDateTime(),
            resultSet.getTimestamp("end_time").toLocalDateTime(),
            AppointmentStatus.valueOf(resultSet.getString("status")),
            resultSet.getTimestamp("created_at").toLocalDateTime()
    );

    private final JdbcTemplate jdbcTemplate;

    public AppointmentRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public long insert(long userId, long providerId, long serviceId, long slotId,
                       AppointmentStatus status, LocalDateTime createdAt) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement("""
                    INSERT INTO appointments (user_id, provider_id, service_id, slot_id, status, created_at)
                    VALUES (?, ?, ?, ?, ?, ?)
                    """, new String[] {"appointment_id"});
            statement.setLong(1, userId);
            statement.setLong(2, providerId);
            statement.setLong(3, serviceId);
            statement.setLong(4, slotId);
            statement.setString(5, status.name());
            statement.setTimestamp(6, Timestamp.valueOf(createdAt));
            return statement;
        }, keyHolder);
        return keyHolder.getKey().longValue();
    }

    public Optional<AppointmentDto> findById(long appointmentId) {
        return jdbcTemplate.query(SELECT_APPOINTMENT + " WHERE appointment.appointment_id = ?",
                APPOINTMENT_ROW_MAPPER, appointmentId).stream().findFirst();
    }

    public List<AppointmentDto> findByUserId(long userId) {
        return jdbcTemplate.query(SELECT_APPOINTMENT + """
                 WHERE appointment.user_id = ?
                 ORDER BY slot.start_time DESC, appointment.appointment_id DESC
                """, APPOINTMENT_ROW_MAPPER, userId);
    }

    /**
     * Locks one appointment row for the rest of the transaction and returns its current status.
     */
    public Optional<AppointmentStatus> lockStatusById(long appointmentId) {
        return jdbcTemplate.query(
                "SELECT status FROM appointments WHERE appointment_id = ? FOR UPDATE",
                (resultSet, rowNum) -> AppointmentStatus.valueOf(resultSet.getString("status")),
                appointmentId
        ).stream().findFirst();
    }

    /**
     * Conditional status change; returns 0 if the appointment was no longer in {@code expected}.
     */
    public int updateStatus(long appointmentId, AppointmentStatus expected, AppointmentStatus next) {
        return jdbcTemplate.update(
                "UPDATE appointments SET status = ? WHERE appointment_id = ? AND status = ?",
                next.name(),
                appointmentId,
                expected.name()
        );
    }

    public List<AppointmentDto> findByProviderId(long providerId) {
        return jdbcTemplate.query(SELECT_APPOINTMENT + """
                 WHERE appointment.provider_id = ?
                 ORDER BY slot.start_time, appointment.appointment_id
                """, APPOINTMENT_ROW_MAPPER, providerId);
    }

    public List<AppointmentDto> findAll() {
        return jdbcTemplate.query(SELECT_APPOINTMENT + " ORDER BY slot.start_time DESC, appointment.appointment_id DESC",
                APPOINTMENT_ROW_MAPPER);
    }

    public long countActiveForSlot(long slotId) {
        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM appointments WHERE slot_id = ? AND status = 'BOOKED'",
                Long.class,
                slotId
        );
        return count == null ? 0 : count;
    }
}
