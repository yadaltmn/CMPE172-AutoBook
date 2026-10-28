package com.autobook;

import com.autobook.dto.AppointmentDto;
import com.autobook.dto.BookingRequest;
import com.autobook.exception.BookingConflictException;
import com.autobook.mocknotification.MockNotificationFaults;
import com.autobook.mocknotification.MockNotificationService;
import com.autobook.model.AppUser;
import com.autobook.model.AppointmentStatus;
import com.autobook.notification.BookingNotifications;
import com.autobook.notification.NotificationDispatcher;
import com.autobook.notification.NotificationOutboxRepository;
import com.autobook.notification.NotificationType;
import com.autobook.notification.OutboxEntry;
import com.autobook.notification.OutboxStatus;
import com.autobook.repository.AppointmentRepository;
import com.autobook.repository.UserRepository;
import com.autobook.service.AppointmentService;
import com.autobook.service.BookingService;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;
import java.util.function.Predicate;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.support.TransactionTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Commit-then-notify through the transactional outbox, with the mock notification service reached
 * over real HTTP. These tests commit real bookings, so they use their own PostgreSQL database.
 */
@SpringBootTest(
		webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
		properties = {
				"spring.datasource.url=jdbc:tc:postgresql:16-alpine:///autobook_notifications",
				"autobook.notifications.timeout=300ms",
				"autobook.notifications.max-attempts=3",
				"autobook.notifications.max-delivery-rounds=3",
				"autobook.notifications.redelivery-delay=30s"
		})
class NotificationOutboxIntegrationTests {

	private static final AtomicInteger SLOT_SEQUENCE = new AtomicInteger();

	@Autowired
	private BookingService bookingService;

	@Autowired
	private AppointmentService appointmentService;

	@Autowired
	private BookingNotifications bookingNotifications;

	@Autowired
	private NotificationDispatcher dispatcher;

	@Autowired
	private NotificationOutboxRepository outboxRepository;

	@Autowired
	private AppointmentRepository appointmentRepository;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private MockNotificationService notificationService;

	@Autowired
	private MockNotificationFaults faults;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Autowired
	private TransactionTemplate transactionTemplate;

	private AppUser jada;

	@BeforeEach
	void setUp() {
		notificationService.clear();
		faults.reset();
		jada = userRepository.findByEmail("jada.nguyen@example.com").orElseThrow();
	}

	@AfterEach
	void tearDown() {
		faults.reset();
	}

	@Test
	void bookingIsSavedFirstAndConfirmationIsDeliveredAfterCommit() throws Exception {
		AppointmentDto appointment = bookingService.book(jada, new BookingRequest(createFutureSlot(), null));
		String key = BookingNotifications.idempotencyKey(NotificationType.BOOKING_CONFIRMED, appointment.appointmentId());

		OutboxEntry sent = awaitOutbox(key, entry -> entry.status() == OutboxStatus.SENT);

		assertEquals(1, sent.attempts());
		assertNotNull(sent.externalNotificationId());
		assertTrue(notificationService.hasDelivered(key));
		assertTrue(sent.message().contains("AB-%05d".formatted(appointment.appointmentId())), sent.message());
		assertEquals(AppointmentStatus.BOOKED, statusOf(appointment.appointmentId()));
	}

	@Test
	void notificationServiceOutageDoesNotUndoTheBookingAndRecoversLater() throws Exception {
		faults.apply(new MockNotificationFaults.FaultSettings(true, 0, 0));

		AppointmentDto appointment = bookingService.book(jada, new BookingRequest(createFutureSlot(), null));
		String key = BookingNotifications.idempotencyKey(NotificationType.BOOKING_CONFIRMED, appointment.appointmentId());

		// Fallback: the first round used all 3 attempts, the row waits in the outbox for a later round.
		OutboxEntry waiting = awaitOutbox(key, entry -> entry.deliveryRounds() == 1);
		assertEquals(OutboxStatus.PENDING, waiting.status());
		assertEquals(3, waiting.attempts());
		assertTrue(waiting.lastError().contains("503"), waiting.lastError());
		assertTrue(waiting.nextAttemptAt().isAfter(LocalDateTime.now()), "retry is scheduled for later");
		assertFalse(notificationService.hasDelivered(key));

		// The committed booking is untouched by the failed notification.
		assertEquals(AppointmentStatus.BOOKED, statusOf(appointment.appointmentId()));
		assertFalse(slotAvailable(appointment.slotId()));

		// The service comes back; the retry job delivers the queued notification.
		faults.reset();
		makeDue(key);
		dispatcher.retryDueNotifications();

		OutboxEntry sent = awaitOutbox(key, entry -> entry.status() == OutboxStatus.SENT);
		assertEquals(4, sent.attempts());
		assertEquals(2, sent.deliveryRounds());
		assertTrue(notificationService.hasDelivered(key));
	}

	@Test
	void transientFailuresAreRetriedWithinOneRound() throws Exception {
		faults.apply(new MockNotificationFaults.FaultSettings(false, 2, 0));

		AppointmentDto appointment = bookingService.book(jada, new BookingRequest(createFutureSlot(), null));
		String key = BookingNotifications.idempotencyKey(NotificationType.BOOKING_CONFIRMED, appointment.appointmentId());

		OutboxEntry sent = awaitOutbox(key, entry -> entry.status() == OutboxStatus.SENT);
		assertEquals(3, sent.attempts(), "two 503s, then success on the third attempt");
		assertEquals(1, sent.deliveryRounds());
		assertEquals(1, notificationService.delivered().size());
	}

	@Test
	void timedOutAttemptsAreRetriedAndIdempotencyPreventsADuplicateNotification() throws Exception {
		// Every attempt takes 1 s, but the gateway gives up after 300 ms.
		faults.apply(new MockNotificationFaults.FaultSettings(false, 0, 1000));

		AppointmentDto appointment = bookingService.book(jada, new BookingRequest(createFutureSlot(), null));
		String key = BookingNotifications.idempotencyKey(NotificationType.BOOKING_CONFIRMED, appointment.appointmentId());

		OutboxEntry waiting = awaitOutbox(key, entry -> entry.deliveryRounds() == 1);
		assertEquals(OutboxStatus.PENDING, waiting.status());
		assertEquals(3, waiting.attempts());
		assertTrue(waiting.lastError().contains("did not answer within 300 ms"), waiting.lastError());

		// The slow service did finish the first request after AutoBook stopped waiting.
		awaitCondition(() -> notificationService.hasDelivered(key), "late delivery of the timed-out request");

		// Retrying after recovery is acknowledged as a duplicate: the customer is notified only once.
		faults.reset();
		makeDue(key);
		dispatcher.retryDueNotifications();

		OutboxEntry sent = awaitOutbox(key, entry -> entry.status() == OutboxStatus.SENT);
		assertEquals(OutboxStatus.SENT, sent.status());
		assertEquals(1, notificationService.delivered().stream()
				.filter(delivered -> delivered.request().idempotencyKey().equals(key)).count());
		assertTrue(notificationService.receivedRequests() >= 4, "3 timed-out attempts plus the retry");

		// Let the server finish the other slow requests so they cannot affect the next test.
		Thread.sleep(1500);
	}

	@Test
	void notificationIsMarkedFailedAfterTheLastRoundButTheBookingStays() throws Exception {
		faults.apply(new MockNotificationFaults.FaultSettings(true, 0, 0));

		AppointmentDto appointment = bookingService.book(jada, new BookingRequest(createFutureSlot(), null));
		String key = BookingNotifications.idempotencyKey(NotificationType.BOOKING_CONFIRMED, appointment.appointmentId());
		awaitOutbox(key, entry -> entry.deliveryRounds() == 1);

		makeDue(key);
		dispatcher.retryDueNotifications();
		awaitOutbox(key, entry -> entry.deliveryRounds() == 2);

		makeDue(key);
		dispatcher.retryDueNotifications();
		OutboxEntry failed = awaitOutbox(key, entry -> entry.status() == OutboxStatus.FAILED);

		assertEquals(3, failed.deliveryRounds());
		assertEquals(9, failed.attempts());
		assertEquals(AppointmentStatus.BOOKED, statusOf(appointment.appointmentId()));
		assertFalse(notificationService.hasDelivered(key));
	}

	@Test
	void rolledBackBookingQueuesAndSendsNothing() throws Exception {
		long slotId = createFutureSlot();
		Long outboxRowsBefore = outboxRows();

		AppointmentDto appointment = transactionTemplate.execute(status -> {
			AppointmentDto booked = bookingService.book(jada, new BookingRequest(slotId, null));
			status.setRollbackOnly();
			return booked;
		});

		Thread.sleep(500);
		assertEquals(outboxRowsBefore, outboxRows());
		assertTrue(appointmentRepository.findById(appointment.appointmentId()).isEmpty());
		assertEquals(0, notificationService.receivedRequests());
	}

	@Test
	void rejectedDoubleBookingSendsNoNotification() throws Exception {
		long slotId = createFutureSlot();
		AppointmentDto first = bookingService.book(jada, new BookingRequest(slotId, null));
		awaitOutbox(BookingNotifications.idempotencyKey(NotificationType.BOOKING_CONFIRMED, first.appointmentId()),
				entry -> entry.status() == OutboxStatus.SENT);
		AppUser alex = userRepository.findByEmail("alex.rivera@example.com").orElseThrow();
		Long outboxRowsBefore = outboxRows();

		assertThrows(BookingConflictException.class, () -> bookingService.book(alex, new BookingRequest(slotId, null)));

		assertEquals(outboxRowsBefore, outboxRows());
		assertEquals(1, notificationService.delivered().size());
	}

	@Test
	void cancellationSendsItsOwnNotificationAndKeepsHistory() throws Exception {
		AppointmentDto appointment = bookingService.book(jada, new BookingRequest(createFutureSlot(), null));
		awaitOutbox(BookingNotifications.idempotencyKey(NotificationType.BOOKING_CONFIRMED, appointment.appointmentId()),
				entry -> entry.status() == OutboxStatus.SENT);

		appointmentService.cancel(jada, appointment.appointmentId());
		String cancelKey = BookingNotifications.idempotencyKey(NotificationType.BOOKING_CANCELLED, appointment.appointmentId());
		OutboxEntry cancelled = awaitOutbox(cancelKey, entry -> entry.status() == OutboxStatus.SENT);

		assertTrue(cancelled.subject().contains("cancelled"));
		assertEquals(2, outboxRepository.findByAppointmentId(appointment.appointmentId()).size());
		assertEquals(AppointmentStatus.CANCELLED, statusOf(appointment.appointmentId()));
		assertTrue(slotAvailable(appointment.slotId()));
	}

	@Test
	void queueingTheSameEventTwiceKeepsOneOutboxRow() throws Exception {
		AppointmentDto appointment = bookingService.book(jada, new BookingRequest(createFutureSlot(), null));
		String key = BookingNotifications.idempotencyKey(NotificationType.BOOKING_CONFIRMED, appointment.appointmentId());
		awaitOutbox(key, entry -> entry.status() == OutboxStatus.SENT);

		transactionTemplate.executeWithoutResult(status -> bookingNotifications.bookingConfirmed(appointment));

		assertEquals(1, outboxRepository.findByAppointmentId(appointment.appointmentId()).size());
		assertEquals(OutboxStatus.SENT, outboxRepository.findByIdempotencyKey(key).orElseThrow().status());
	}

	@Test
	void queueingOutsideATransactionIsRejected() {
		AppointmentDto appointment = appointmentRepository.findById(1L).orElseThrow();

		assertThrows(IllegalTransactionStateException.class,
				() -> bookingNotifications.bookingConfirmed(appointment));
	}

	private long createFutureSlot() {
		int sequence = SLOT_SEQUENCE.incrementAndGet();
		LocalDateTime start = LocalDateTime.now().plusDays(20 + sequence).withHour(8).withMinute(0).withSecond(0).withNano(0);
		jdbcTemplate.update("""
				INSERT INTO availability_slots (provider_id, service_id, start_time, end_time, is_available)
				VALUES (2, 1, ?, ?, TRUE)
				""", Timestamp.valueOf(start), Timestamp.valueOf(start.plusMinutes(45)));
		return jdbcTemplate.queryForObject("SELECT MAX(slot_id) FROM availability_slots", Long.class);
	}

	private void makeDue(String key) {
		jdbcTemplate.update("UPDATE notification_outbox SET next_attempt_at = ? WHERE idempotency_key = ?",
				Timestamp.valueOf(LocalDateTime.now().minusSeconds(1)), key);
	}

	private OutboxEntry awaitOutbox(String key, Predicate<OutboxEntry> condition) throws InterruptedException {
		long deadline = System.currentTimeMillis() + 15_000;
		OutboxEntry latest = null;
		while (System.currentTimeMillis() < deadline) {
			latest = outboxRepository.findByIdempotencyKey(key).orElse(null);
			if (latest != null && condition.test(latest)) {
				return latest;
			}
			Thread.sleep(50);
		}
		throw new AssertionError("Outbox entry " + key + " did not reach the expected state; last seen: " + latest);
	}

	private void awaitCondition(BooleanSupplier condition, String description) throws InterruptedException {
		long deadline = System.currentTimeMillis() + 10_000;
		while (!condition.getAsBoolean()) {
			if (System.currentTimeMillis() > deadline) {
				throw new AssertionError("Timed out waiting for " + description);
			}
			Thread.sleep(50);
		}
	}

	private AppointmentStatus statusOf(long appointmentId) {
		return AppointmentStatus.valueOf(jdbcTemplate.queryForObject(
				"SELECT status FROM appointments WHERE appointment_id = ?", String.class, appointmentId));
	}

	private boolean slotAvailable(long slotId) {
		return Boolean.TRUE.equals(jdbcTemplate.queryForObject(
				"SELECT is_available FROM availability_slots WHERE slot_id = ?", Boolean.class, slotId));
	}

	private Long outboxRows() {
		return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM notification_outbox", Long.class);
	}
}
