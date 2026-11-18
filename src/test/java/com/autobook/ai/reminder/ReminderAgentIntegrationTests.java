package com.autobook.ai.reminder;

import com.autobook.ai.AiTestModels;
import com.autobook.ai.ScriptedChatModel;
import com.autobook.dto.BookingRequest;
import com.autobook.mocknotification.MockNotificationFaults;
import com.autobook.mocknotification.MockNotificationService;
import com.autobook.model.AppUser;
import com.autobook.notification.BookingNotifications;
import com.autobook.notification.NotificationOutboxRepository;
import com.autobook.notification.NotificationType;
import com.autobook.notification.OutboxEntry;
import com.autobook.notification.OutboxStatus;
import com.autobook.repository.UserRepository;
import com.autobook.service.AppointmentService;
import com.autobook.service.BookingService;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Predicate;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.context.support.WithUserDetails;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.hasItem;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The scheduled reminder agent end to end: selection, Gemini-written text (scripted model), delivery
 * through the real outbox and mock notification service over HTTP, duplicate prevention, the admin
 * trigger, and failure isolation. Runs on its own database because it commits data.
 */
@SpringBootTest(
		webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
		properties = {
				"spring.datasource.url=jdbc:tc:postgresql:16-alpine:///autobook_reminders",
				"autobook.notifications.timeout=300ms",
				"autobook.notifications.max-attempts=2"
		})
@AutoConfigureMockMvc
@Import(AiTestModels.class)
class ReminderAgentIntegrationTests {

	private static final String ADMIN = "morgan.lee@example.com";
	private static final String CUSTOMER = "jada.nguyen@example.com";
	private static final AtomicInteger MINUTES = new AtomicInteger();

	@Autowired
	private ReminderAgentService reminderAgent;

	@Autowired
	private ScriptedChatModel chatModel;

	@Autowired
	private BookingService bookingService;

	@Autowired
	private AppointmentService appointmentService;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private NotificationOutboxRepository outboxRepository;

	@Autowired
	private MockNotificationService notificationService;

	@Autowired
	private MockNotificationFaults faults;

	@Autowired
	private JdbcTemplate jdbc;

	@Autowired
	private MockMvc mockMvc;

	private final ExecutorService executor = Executors.newFixedThreadPool(2);

	@BeforeEach
	void setUp() {
		chatModel.reset();
		faults.reset();
		notificationService.clear();
		// Clear earlier tests' due appointments out of the way so each test sees only its own.
		reminderAgent.run(ReminderTrigger.MANUAL);
		chatModel.reset();
	}

	@AfterEach
	void tearDown() {
		faults.reset();
		executor.shutdownNow();
	}

	@Test
	void onlyBookedAppointmentsStartingWithinTheWindowAreReminded() {
		long inWindow = book(LocalDateTime.now().plusHours(3));
		long tooFar = book(LocalDateTime.now().plusDays(3));
		long cancelled = book(LocalDateTime.now().plusHours(4));
		appointmentService.cancel(customer(), cancelled);
		chatModel.script((prompt, tools) -> "Hi Jada, see you soon.");

		ReminderRunResult result = reminderAgent.run(ReminderTrigger.MANUAL);

		List<Long> reminded = result.items().stream().map(ReminderRunResult.Item::appointmentId).toList();
		assertTrue(reminded.contains(inWindow));
		assertFalse(reminded.contains(tooFar));
		assertFalse(reminded.contains(cancelled));
		assertEquals(1, reminders(inWindow));
		assertEquals(0, reminders(tooFar));
		assertEquals(0, reminders(cancelled));
	}

	@Test
	void geminiWritesThePersonalizedReminderAndTheNotificationServiceDeliversIt() throws Exception {
		long appointmentId = book(LocalDateTime.now().plusHours(5));
		chatModel.script((prompt, tools) -> "Hi Jada! Your **Oil Change** at Downtown Auto Care is coming up soon. "
				+ "Confirmation " + BookingNotifications.confirmationNumber(appointmentDto(appointmentId)) + ".");

		ReminderRunResult result = reminderAgent.run(ReminderTrigger.MANUAL);

		ReminderRunResult.Item item = item(result, appointmentId);
		assertEquals(ReminderRunResult.Outcome.QUEUED, item.outcome());
		assertEquals(ReminderContentSource.GEMINI, item.source());
		String prompt = ScriptedChatModel.text(chatModel.lastPrompt());
		assertTrue(prompt.contains("Customer first name: Jada"));
		assertTrue(prompt.contains("Service: Oil Change"));
		assertTrue(prompt.contains("Repair shop: Downtown Auto Care"));
		assertTrue(prompt.contains("Confirmation number: AB-"));
		assertFalse(prompt.contains("@example.com"), "the customer's email is not sent to the model");

		String key = BookingNotifications.idempotencyKey(NotificationType.APPOINTMENT_REMINDER, appointmentId);
		OutboxEntry sent = awaitOutbox(key, entry -> entry.status() == OutboxStatus.SENT);
		assertEquals("Hi Jada! Your Oil Change at Downtown Auto Care is coming up soon. Confirmation "
				+ BookingNotifications.confirmationNumber(appointmentDto(appointmentId)) + ".", sent.message(),
				"Gemini's text, cleaned of markdown");
		assertTrue(notificationService.hasDelivered(key));
		assertEquals("GEMINI", jdbc.queryForObject(
				"SELECT content_source FROM appointment_reminders WHERE appointment_id = ?", String.class, appointmentId));
	}

	@Test
	void runningAgainOrConcurrentlyNeverSendsADuplicateReminder() throws Exception {
		long appointmentId = book(LocalDateTime.now().plusHours(6));
		chatModel.script((prompt, tools) -> "Reminder for your appointment.");

		CountDownLatch start = new CountDownLatch(1);
		Future<ReminderRunResult> scheduled = executor.submit(() -> {
			start.await();
			return reminderAgent.run(ReminderTrigger.SCHEDULED);
		});
		Future<ReminderRunResult> manual = executor.submit(() -> {
			start.await();
			return reminderAgent.run(ReminderTrigger.MANUAL);
		});
		start.countDown();
		scheduled.get(30, TimeUnit.SECONDS);
		manual.get(30, TimeUnit.SECONDS);
		ReminderRunResult again = reminderAgent.run(ReminderTrigger.MANUAL);

		assertFalse(again.items().stream().anyMatch(item -> item.appointmentId() == appointmentId
				&& item.outcome() == ReminderRunResult.Outcome.QUEUED));
		assertEquals(1, reminders(appointmentId));
		String key = BookingNotifications.idempotencyKey(NotificationType.APPOINTMENT_REMINDER, appointmentId);
		awaitOutbox(key, entry -> entry.status() == OutboxStatus.SENT);
		assertEquals(1, notificationService.delivered().stream()
				.filter(delivered -> delivered.request().idempotencyKey().equals(key)).count());
		assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM notification_outbox WHERE idempotency_key = ?",
				Integer.class, key));
	}

	@Test
	@WithUserDetails(ADMIN)
	void adminTriggerRunsTheRealReminderJob() throws Exception {
		long appointmentId = book(LocalDateTime.now().plusHours(7));
		chatModel.script((prompt, tools) -> "Hi Jada, reminder from the admin-triggered run.");

		mockMvc.perform(post("/api/admin/ai/reminders/run").with(csrf()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.trigger").value("MANUAL"))
				.andExpect(jsonPath("$.items[*].appointmentId", hasItem((int) appointmentId)));

		assertEquals(1, reminders(appointmentId));
		assertEquals("MANUAL", jdbc.queryForObject(
				"SELECT trigger_source FROM appointment_reminders WHERE appointment_id = ?", String.class, appointmentId));
		mockMvc.perform(get("/api/admin/ai/reminders"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[*].message", hasItem("Hi Jada, reminder from the admin-triggered run.")));
	}

	@Test
	@WithUserDetails(CUSTOMER)
	void customersCannotTriggerReminders() throws Exception {
		long appointmentId = book(LocalDateTime.now().plusHours(8));

		mockMvc.perform(post("/api/admin/ai/reminders/run").with(csrf())).andExpect(status().isForbidden());
		mockMvc.perform(get("/api/admin/ai/reminders")).andExpect(status().isForbidden());

		assertEquals(0, reminders(appointmentId));
	}

	@Test
	void anonymousUsersCannotTriggerReminders() throws Exception {
		long appointmentId = book(LocalDateTime.now().plusHours(8));

		mockMvc.perform(post("/api/admin/ai/reminders/run").with(csrf())).andExpect(status().isUnauthorized());

		assertEquals(0, reminders(appointmentId));
	}

	@Test
	@WithUserDetails(ADMIN)
	void adminTriggerRequiresCsrfToken() throws Exception {
		long appointmentId = book(LocalDateTime.now().plusHours(8));

		mockMvc.perform(post("/api/admin/ai/reminders/run")).andExpect(status().isForbidden());

		assertEquals(0, reminders(appointmentId));
	}

	@Test
	void notificationServiceOutageDoesNotAffectTheBooking() throws Exception {
		long appointmentId = book(LocalDateTime.now().plusHours(9));
		long slotId = jdbc.queryForObject("SELECT slot_id FROM appointments WHERE appointment_id = ?", Long.class,
				appointmentId);
		faults.apply(new MockNotificationFaults.FaultSettings(true, 0, 0));
		chatModel.script((prompt, tools) -> "Reminder text.");

		ReminderRunResult result = reminderAgent.run(ReminderTrigger.MANUAL);

		assertEquals(ReminderRunResult.Outcome.QUEUED, item(result, appointmentId).outcome());
		String key = BookingNotifications.idempotencyKey(NotificationType.APPOINTMENT_REMINDER, appointmentId);
		OutboxEntry waiting = awaitOutbox(key, entry -> entry.deliveryRounds() >= 1);
		assertEquals(OutboxStatus.PENDING, waiting.status(), "kept for a later delivery round");
		assertEquals("BOOKED", jdbc.queryForObject(
				"SELECT status FROM appointments WHERE appointment_id = ?", String.class, appointmentId));
		assertFalse(Boolean.TRUE.equals(jdbc.queryForObject(
				"SELECT is_available FROM availability_slots WHERE slot_id = ?", Boolean.class, slotId)));
	}

	@Test
	void geminiFailureFarFromTheAppointmentIsDeferredThenRetried() {
		long appointmentId = book(LocalDateTime.now().plusHours(12));
		chatModel.failWith(new IllegalStateException("simulated Gemini outage"));

		ReminderRunResult failed = reminderAgent.run(ReminderTrigger.SCHEDULED);

		assertEquals(ReminderRunResult.Outcome.DEFERRED, item(failed, appointmentId).outcome());
		assertEquals(0, reminders(appointmentId), "nothing recorded as sent");

		chatModel.reset();
		chatModel.script((prompt, tools) -> "Hi Jada, Gemini is back.");
		ReminderRunResult retried = reminderAgent.run(ReminderTrigger.SCHEDULED);

		assertEquals(ReminderContentSource.GEMINI, item(retried, appointmentId).source());
	}

	@Test
	void geminiFailureCloseToTheAppointmentSendsTheLabelledTemplate() {
		long appointmentId = book(LocalDateTime.now().plusHours(2));
		chatModel.failWith(new IllegalStateException("simulated Gemini outage"));

		ReminderRunResult result = reminderAgent.run(ReminderTrigger.SCHEDULED);

		ReminderRunResult.Item item = item(result, appointmentId);
		assertEquals(ReminderContentSource.TEMPLATE, item.source(), "recorded as TEMPLATE, not as AI-written");
		assertNull(jdbc.queryForObject("SELECT model FROM appointment_reminders WHERE appointment_id = ?",
				String.class, appointmentId));
		String message = outboxRepository.findById(item.outboxId()).orElseThrow().message();
		assertTrue(message.startsWith("Hi Jada, this is a reminder of your Oil Change appointment at Downtown Auto Care"));
	}

	private long book(LocalDateTime approximateStart) {
		LocalDateTime start = approximateStart.withSecond(0).withNano(0).plusMinutes(MINUTES.incrementAndGet());
		jdbc.update("""
				INSERT INTO availability_slots (provider_id, service_id, start_time, end_time, is_available)
				VALUES (1, 1, ?, ?, TRUE)
				""", Timestamp.valueOf(start), Timestamp.valueOf(start.plusMinutes(45)));
		long slotId = jdbc.queryForObject("SELECT MAX(slot_id) FROM availability_slots", Long.class);
		return bookingService.book(customer(), new BookingRequest(slotId, null)).appointmentId();
	}

	private AppUser customer() {
		return userRepository.findByEmail(CUSTOMER).orElseThrow();
	}

	private com.autobook.dto.AppointmentDto appointmentDto(long appointmentId) {
		return appointmentService.getAppointment(customer(), appointmentId);
	}

	private int reminders(long appointmentId) {
		return jdbc.queryForObject("SELECT COUNT(*) FROM appointment_reminders WHERE appointment_id = ?",
				Integer.class, appointmentId);
	}

	private static ReminderRunResult.Item item(ReminderRunResult result, long appointmentId) {
		return result.items().stream().filter(item -> item.appointmentId() == appointmentId).findFirst()
				.orElseThrow(() -> new AssertionError("appointment " + appointmentId + " not in run " + result));
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
}
