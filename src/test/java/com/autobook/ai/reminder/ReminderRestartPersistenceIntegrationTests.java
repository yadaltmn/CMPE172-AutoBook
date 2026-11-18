package com.autobook.ai.reminder;

import com.autobook.AutobookApplication;
import com.autobook.dto.BookingRequest;
import com.autobook.model.AppUser;
import com.autobook.repository.UserRepository;
import com.autobook.service.BookingService;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * Reminder tracking is stored in PostgreSQL, so a restarted application does not remind the same
 * appointment again. Also shows the agent working without any Gemini key (template fallback).
 */
@Testcontainers
class ReminderRestartPersistenceIntegrationTests {

	@Container
	static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16-alpine");

	@Test
	void remindersAreNotRepeatedAfterARestart() {
		long appointmentId;
		try (ConfigurableApplicationContext firstRun = startAutoBook()) {
			JdbcTemplate jdbc = firstRun.getBean(JdbcTemplate.class);
			LocalDateTime start = LocalDateTime.now().plusHours(3).withSecond(0).withNano(0);
			jdbc.update("""
					INSERT INTO availability_slots (provider_id, service_id, start_time, end_time, is_available)
					VALUES (1, 1, ?, ?, TRUE)
					""", Timestamp.valueOf(start), Timestamp.valueOf(start.plusMinutes(45)));
			long slotId = jdbc.queryForObject("SELECT MAX(slot_id) FROM availability_slots", Long.class);
			AppUser jada = firstRun.getBean(UserRepository.class).findByEmail("jada.nguyen@example.com").orElseThrow();
			appointmentId = firstRun.getBean(BookingService.class).book(jada, new BookingRequest(slotId, null)).appointmentId();

			ReminderRunResult result = firstRun.getBean(ReminderAgentService.class).run(ReminderTrigger.MANUAL);

			ReminderRunResult.Item item = result.items().stream()
					.filter(candidate -> candidate.appointmentId() == appointmentId).findFirst().orElseThrow();
			assertEquals(ReminderRunResult.Outcome.QUEUED, item.outcome());
			assertEquals(ReminderContentSource.TEMPLATE, item.source(), "no Gemini key: labelled template");
		}

		try (ConfigurableApplicationContext secondRun = startAutoBook()) {
			ReminderRunResult result = secondRun.getBean(ReminderAgentService.class).run(ReminderTrigger.SCHEDULED);

			assertFalse(result.items().stream().anyMatch(item -> item.appointmentId() == appointmentId));
			JdbcTemplate jdbc = secondRun.getBean(JdbcTemplate.class);
			assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM appointment_reminders WHERE appointment_id = ?",
					Integer.class, appointmentId));
			assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM notification_outbox WHERE idempotency_key = ?",
					Integer.class, "APPOINTMENT_REMINDER-appointment-" + appointmentId));
		}
	}

	private static ConfigurableApplicationContext startAutoBook() {
		return new SpringApplicationBuilder(AutobookApplication.class).run(
				"--server.port=0",
				"--spring.datasource.url=" + postgres.getJdbcUrl(),
				"--spring.datasource.username=" + postgres.getUsername(),
				"--spring.datasource.password=" + postgres.getPassword(),
				"--spring.datasource.driver-class-name=org.postgresql.Driver");
	}
}
