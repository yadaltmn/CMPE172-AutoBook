package com.autobook;

import com.autobook.dto.AppointmentDto;
import com.autobook.dto.BookingRequest;
import com.autobook.exception.BookingConflictException;
import com.autobook.model.AppUser;
import com.autobook.model.AppointmentStatus;
import com.autobook.repository.AppointmentRepository;
import com.autobook.repository.UserRepository;
import com.autobook.service.AppointmentService;
import com.autobook.service.BookingService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Starts AutoBook, books and cancels appointments, shuts the application down, starts it again on
 * the same PostgreSQL database, and checks that every booking, the cancellation history, and the
 * notification outbox are still there and that the seed data was not applied twice.
 */
@Testcontainers
class ApplicationRestartPersistenceIntegrationTests {

	@Container
	static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16-alpine");

	@Test
	void bookingsAndCancellationHistorySurviveAnApplicationRestart() {
		long bookedId;
		long cancelledId;
		long bookedSlotId;
		long cancelledSlotId;
		long outboxRows;

		try (ConfigurableApplicationContext firstRun = startAutoBook()) {
			BookingService bookingService = firstRun.getBean(BookingService.class);
			AppointmentService appointmentService = firstRun.getBean(AppointmentService.class);
			AppUser jada = customer(firstRun, "jada.nguyen@example.com");
			AppUser alex = customer(firstRun, "alex.rivera@example.com");

			// Seed slots 1 and 2 are open, upcoming slots.
			AppointmentDto booked = bookingService.book(jada, new BookingRequest(1L, null));
			AppointmentDto toCancel = bookingService.book(alex, new BookingRequest(2L, null));
			appointmentService.cancel(alex, toCancel.appointmentId());

			bookedId = booked.appointmentId();
			cancelledId = toCancel.appointmentId();
			bookedSlotId = booked.slotId();
			cancelledSlotId = toCancel.slotId();
			outboxRows = count(firstRun, "SELECT COUNT(*) FROM notification_outbox");
			assertEquals(3, outboxRows, "two confirmations and one cancellation were queued");
		}

		try (ConfigurableApplicationContext secondRun = startAutoBook()) {
			AppointmentRepository appointments = secondRun.getBean(AppointmentRepository.class);

			assertEquals(AppointmentStatus.BOOKED, appointments.findById(bookedId).orElseThrow().status());
			assertEquals(AppointmentStatus.CANCELLED, appointments.findById(cancelledId).orElseThrow().status());
			assertFalse(slotAvailable(secondRun, bookedSlotId), "booked slot is still taken");
			assertTrue(slotAvailable(secondRun, cancelledSlotId), "cancelled slot is still open");
			assertEquals(outboxRows, count(secondRun, "SELECT COUNT(*) FROM notification_outbox"));

			// Migrations ran once; the restart did not drop tables or re-insert the seed data.
			assertEquals(3, count(secondRun, "SELECT COUNT(*) FROM flyway_schema_history WHERE success"));
			assertEquals(5, count(secondRun, "SELECT COUNT(*) FROM users"));
			assertEquals(6, count(secondRun, "SELECT COUNT(*) FROM appointments"));

			// Double-booking protection still sees the persisted booking.
			AppUser alex = customer(secondRun, "alex.rivera@example.com");
			assertThrows(BookingConflictException.class,
					() -> secondRun.getBean(BookingService.class).book(alex, new BookingRequest(bookedSlotId, null)));
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

	private static AppUser customer(ConfigurableApplicationContext context, String email) {
		return context.getBean(UserRepository.class).findByEmail(email).orElseThrow();
	}

	private static long count(ConfigurableApplicationContext context, String sql) {
		Long value = context.getBean(JdbcTemplate.class).queryForObject(sql, Long.class);
		return value == null ? 0 : value;
	}

	private static boolean slotAvailable(ConfigurableApplicationContext context, long slotId) {
		return Boolean.TRUE.equals(context.getBean(JdbcTemplate.class).queryForObject(
				"SELECT is_available FROM availability_slots WHERE slot_id = ?", Boolean.class, slotId));
	}
}
