package com.autobook.notification;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

/**
 * JDBC access to {@code notification_outbox}. All SQL for the outbox lives here.
 */
@Repository
public class NotificationOutboxRepository {

    private static final RowMapper<OutboxEntry> ROW_MAPPER = NotificationOutboxRepository::mapRow;

    private final JdbcTemplate jdbcTemplate;

    public NotificationOutboxRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * Inserts a PENDING notification unless one with the same idempotency key already exists.
     *
     * @return the new outbox id, or empty if the event was already queued
     */
    public Optional<Long> insertIfAbsent(NotificationRequest request, LocalDateTime now) {
        List<Long> ids = jdbcTemplate.query("""
                        INSERT INTO notification_outbox (idempotency_key, appointment_id, notification_type,
                            recipient_email, recipient_name, subject, message, confirmation_number,
                            status, created_at, next_attempt_at)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, 'PENDING', ?, ?)
                        ON CONFLICT (idempotency_key) DO NOTHING
                        RETURNING outbox_id
                        """,
                (resultSet, rowNum) -> resultSet.getLong("outbox_id"),
                request.idempotencyKey(), request.appointmentId(), request.type().name(),
                request.recipientEmail(), request.recipientName(), request.subject(), request.message(),
                request.confirmationNumber(), Timestamp.valueOf(now), Timestamp.valueOf(now));
        return ids.stream().findFirst();
    }

    public Optional<OutboxEntry> findById(long outboxId) {
        return jdbcTemplate.query("SELECT * FROM notification_outbox WHERE outbox_id = ?", ROW_MAPPER, outboxId)
                .stream().findFirst();
    }

    public Optional<OutboxEntry> findByIdempotencyKey(String idempotencyKey) {
        return jdbcTemplate.query("SELECT * FROM notification_outbox WHERE idempotency_key = ?", ROW_MAPPER,
                idempotencyKey).stream().findFirst();
    }

    public List<OutboxEntry> findByAppointmentId(long appointmentId) {
        return jdbcTemplate.query("SELECT * FROM notification_outbox WHERE appointment_id = ? ORDER BY outbox_id",
                ROW_MAPPER, appointmentId);
    }

    public List<OutboxEntry> findRecent(int limit) {
        return jdbcTemplate.query("SELECT * FROM notification_outbox ORDER BY outbox_id DESC LIMIT ?",
                ROW_MAPPER, limit);
    }

    /**
     * PENDING rows whose next attempt is due, oldest first.
     */
    public List<Long> findDueIds(LocalDateTime now, int limit) {
        return jdbcTemplate.query("""
                        SELECT outbox_id FROM notification_outbox
                        WHERE status = 'PENDING' AND next_attempt_at <= ?
                        ORDER BY next_attempt_at, outbox_id
                        LIMIT ?
                        """,
                (resultSet, rowNum) -> resultSet.getLong("outbox_id"), Timestamp.valueOf(now), limit);
    }

    /**
     * Claims a due PENDING row for one delivery round by pushing its next attempt time to
     * {@code leaseUntil}. Only one caller can win the conditional update, so the on-commit dispatch
     * and the retry job never deliver the same row at the same time. If the process dies mid-round,
     * the lease simply expires and the retry job picks the row up again.
     *
     * @return {@code true} if this caller owns the row for the round
     */
    public boolean claim(long outboxId, LocalDateTime now, LocalDateTime leaseUntil) {
        return jdbcTemplate.update("""
                UPDATE notification_outbox SET next_attempt_at = ?
                WHERE outbox_id = ? AND status = 'PENDING' AND next_attempt_at <= ?
                """, Timestamp.valueOf(leaseUntil), outboxId, Timestamp.valueOf(now)) == 1;
    }

    public void markSent(long outboxId, int attemptsUsed, String externalNotificationId, LocalDateTime now) {
        jdbcTemplate.update("""
                UPDATE notification_outbox
                SET status = 'SENT', attempts = attempts + ?, delivery_rounds = delivery_rounds + 1,
                    external_notification_id = ?, sent_at = ?, last_error = NULL
                WHERE outbox_id = ?
                """, attemptsUsed, externalNotificationId, Timestamp.valueOf(now), outboxId);
    }

    public void scheduleRetry(long outboxId, int attemptsUsed, String error, LocalDateTime nextAttemptAt) {
        jdbcTemplate.update("""
                UPDATE notification_outbox
                SET attempts = attempts + ?, delivery_rounds = delivery_rounds + 1, last_error = ?, next_attempt_at = ?
                WHERE outbox_id = ?
                """, attemptsUsed, truncate(error), Timestamp.valueOf(nextAttemptAt), outboxId);
    }

    public void markFailed(long outboxId, int attemptsUsed, String error) {
        jdbcTemplate.update("""
                UPDATE notification_outbox
                SET status = 'FAILED', attempts = attempts + ?, delivery_rounds = delivery_rounds + 1, last_error = ?
                WHERE outbox_id = ?
                """, attemptsUsed, truncate(error), outboxId);
    }

    public long countByStatus(OutboxStatus status) {
        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM notification_outbox WHERE status = ?", Long.class, status.name());
        return count == null ? 0 : count;
    }

    private static String truncate(String value) {
        if (value == null) {
            return null;
        }
        return value.length() <= 500 ? value : value.substring(0, 500);
    }

    private static OutboxEntry mapRow(ResultSet resultSet, int rowNum) throws SQLException {
        Timestamp sentAt = resultSet.getTimestamp("sent_at");
        return new OutboxEntry(
                resultSet.getLong("outbox_id"),
                resultSet.getString("idempotency_key"),
                resultSet.getLong("appointment_id"),
                NotificationType.valueOf(resultSet.getString("notification_type")),
                resultSet.getString("recipient_email"),
                resultSet.getString("recipient_name"),
                resultSet.getString("subject"),
                resultSet.getString("message"),
                resultSet.getString("confirmation_number"),
                OutboxStatus.valueOf(resultSet.getString("status")),
                resultSet.getInt("attempts"),
                resultSet.getInt("delivery_rounds"),
                resultSet.getString("last_error"),
                resultSet.getString("external_notification_id"),
                resultSet.getTimestamp("created_at").toLocalDateTime(),
                resultSet.getTimestamp("next_attempt_at").toLocalDateTime(),
                sentAt == null ? null : sentAt.toLocalDateTime()
        );
    }
}
