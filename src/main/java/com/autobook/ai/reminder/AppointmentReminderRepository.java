package com.autobook.ai.reminder;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * JDBC access to {@code appointment_reminders}. All reminder SQL lives here.
 */
@Repository
public class AppointmentReminderRepository {

    public static final String UPCOMING_APPOINTMENT = "UPCOMING_APPOINTMENT";

    private final JdbcTemplate jdbcTemplate;

    public AppointmentReminderRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * Booked appointments starting after {@code now} and no later than {@code until} that have no
     * reminder of this type yet, soonest first.
     */
    public List<Long> findDueAppointmentIds(LocalDateTime now, LocalDateTime until, String reminderType, int limit) {
        return jdbcTemplate.query("""
                        SELECT appointment.appointment_id
                        FROM appointments appointment
                        JOIN availability_slots slot ON slot.slot_id = appointment.slot_id
                        WHERE appointment.status = 'BOOKED'
                          AND slot.start_time > ?
                          AND slot.start_time <= ?
                          AND NOT EXISTS (
                              SELECT 1 FROM appointment_reminders reminder
                              WHERE reminder.appointment_id = appointment.appointment_id
                                AND reminder.reminder_type = ?)
                        ORDER BY slot.start_time, appointment.appointment_id
                        LIMIT ?
                        """,
                (resultSet, rowNum) -> resultSet.getLong("appointment_id"),
                Timestamp.valueOf(now), Timestamp.valueOf(until), reminderType, limit);
    }

    public boolean exists(long appointmentId, String reminderType) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM appointment_reminders WHERE appointment_id = ? AND reminder_type = ?",
                Integer.class, appointmentId, reminderType);
        return count != null && count > 0;
    }

    /** Fails with a DuplicateKeyException if this appointment already has a reminder of this type. */
    public void insert(long appointmentId, String reminderType, long outboxId, ReminderContentSource source,
                       String model, ReminderTrigger trigger, LocalDateTime createdAt) {
        jdbcTemplate.update("""
                INSERT INTO appointment_reminders
                    (appointment_id, reminder_type, outbox_id, content_source, model, trigger_source, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """, appointmentId, reminderType, outboxId, source.name(), model, trigger.name(),
                Timestamp.valueOf(createdAt));
    }

    public List<ReminderRecord> findRecent(int limit) {
        return jdbcTemplate.query("""
                        SELECT reminder.reminder_id, reminder.appointment_id, reminder.reminder_type,
                               reminder.content_source, reminder.model, reminder.trigger_source, reminder.created_at,
                               outbox.outbox_id, outbox.idempotency_key, outbox.status, outbox.attempts, outbox.message
                        FROM appointment_reminders reminder
                        JOIN notification_outbox outbox ON outbox.outbox_id = reminder.outbox_id
                        ORDER BY reminder.reminder_id DESC
                        LIMIT ?
                        """,
                (resultSet, rowNum) -> new ReminderRecord(
                        resultSet.getLong("reminder_id"),
                        resultSet.getLong("appointment_id"),
                        resultSet.getString("reminder_type"),
                        resultSet.getString("content_source"),
                        resultSet.getString("model"),
                        resultSet.getString("trigger_source"),
                        resultSet.getTimestamp("created_at").toLocalDateTime(),
                        resultSet.getLong("outbox_id"),
                        resultSet.getString("idempotency_key"),
                        resultSet.getString("status"),
                        resultSet.getInt("attempts"),
                        resultSet.getString("message")),
                limit);
    }
}
