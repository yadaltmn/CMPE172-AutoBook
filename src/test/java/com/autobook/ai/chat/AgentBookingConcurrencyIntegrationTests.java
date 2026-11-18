package com.autobook.ai.chat;

import com.autobook.ai.AiTestModels;
import com.autobook.dto.BookingRequest;
import com.autobook.exception.BookingConflictException;
import com.autobook.model.AppUser;
import com.autobook.repository.UserRepository;
import com.autobook.service.BookingService;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Professor's required test: the AI booking path uses the existing transactional BookingService, so
 * double booking stays impossible when the assistant is involved.
 *
 * <p>Two customers race for the same slot at the same instant. Jada's booking goes through the
 * assistant's actual tool ({@link BookingAgentTools#requestBooking}) and her confirmation
 * ({@link BookingConfirmationService#confirm}); Alex books through the normal {@link BookingService}
 * (and, in the second test, also through the assistant). Nothing is mocked: real services, real
 * row locks, real PostgreSQL. Exactly one active booking may exist afterwards, and the persisted
 * appointment and slot rows are checked directly.
 */
@SpringBootTest(
		webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
		properties = "spring.datasource.url=jdbc:tc:postgresql:16-alpine:///autobook_agent_race")
@Import(AiTestModels.class)
class AgentBookingConcurrencyIntegrationTests {

	private static final int ROUNDS = 5;

	private final ExecutorService executor = Executors.newFixedThreadPool(2);

	@Autowired
	private AgentToolsFactory toolsFactory;

	@Autowired
	private ConversationStore conversations;

	@Autowired
	private BookingConfirmationService confirmations;

	@Autowired
	private BookingService bookingService;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private JdbcTemplate jdbc;

	@AfterEach
	void shutdown() {
		executor.shutdownNow();
	}

	@Test
	void agentBookingAndDirectBookingOfTheSameSlotProduceExactlyOneBooking() throws Exception {
		AppUser jada = customer(AgentTestSupport.JADA);
		AppUser alex = customer(AgentTestSupport.ALEX);

		for (int round = 0; round < ROUNDS; round++) {
			long slotId = AgentTestSupport.createSlot(jdbc, 1, 1, AgentTestSupport.uniqueFutureStart());
			String conversationId = UUID.randomUUID().toString();
			String token = prepareBookingThroughAgentTool(jada, conversationId, slotId);

			List<Boolean> results = race(
					() -> confirmations.confirm(jada, conversationId, token).status()
							== BookingConfirmationService.Status.BOOKED,
					() -> bookDirectly(alex, slotId));

			assertEquals(1, results.stream().filter(Boolean::booleanValue).count(),
					"round " + round + ": exactly one booking succeeds, results " + results);
			assertExactlyOneActiveBooking(slotId);
		}
	}

	@Test
	void twoCustomersConfirmingAgentBookingsForTheSameSlotProduceExactlyOneBooking() throws Exception {
		AppUser jada = customer(AgentTestSupport.JADA);
		AppUser alex = customer(AgentTestSupport.ALEX);

		for (int round = 0; round < ROUNDS; round++) {
			long slotId = AgentTestSupport.createSlot(jdbc, 1, 1, AgentTestSupport.uniqueFutureStart());
			String jadaConversation = UUID.randomUUID().toString();
			String alexConversation = UUID.randomUUID().toString();
			// Both customers are offered the slot while it is still open.
			String jadaToken = prepareBookingThroughAgentTool(jada, jadaConversation, slotId);
			String alexToken = prepareBookingThroughAgentTool(alex, alexConversation, slotId);

			List<BookingConfirmationService.Status> results = race(
					() -> confirmations.confirm(jada, jadaConversation, jadaToken).status(),
					() -> confirmations.confirm(alex, alexConversation, alexToken).status());

			assertEquals(1, results.stream().filter(BookingConfirmationService.Status.BOOKED::equals).count(),
					"round " + round + ": " + results);
			assertEquals(1, results.stream().filter(BookingConfirmationService.Status.CONFLICT::equals).count(),
					"round " + round + ": the competing confirmation is rejected, " + results);
			assertExactlyOneActiveBooking(slotId);
		}
	}

	/** Calls the assistant's real requestBooking tool and returns the confirmation token it created. */
	private String prepareBookingThroughAgentTool(AppUser customer, String conversationId, long slotId) {
		Conversation conversation = conversations.get(customer.userId(), conversationId);
		String result = toolsFactory.create(customer, conversation).requestBooking(slotId);
		assertTrue(result.contains("NOT BOOKED YET"), result);
		assertEquals(0, jdbc.queryForObject(
				"SELECT COUNT(*) FROM appointments WHERE slot_id = ? AND status = 'BOOKED'", Integer.class, slotId));
		return confirmationsTokenFor(customer, conversationId);
	}

	@Autowired
	private PendingActionStore pendingActions;

	private String confirmationsTokenFor(AppUser customer, String conversationId) {
		return pendingActions.current(customer.userId(), conversationId).orElseThrow().token();
	}

	private boolean bookDirectly(AppUser customer, long slotId) {
		try {
			bookingService.book(customer, new BookingRequest(slotId, null));
			return true;
		} catch (BookingConflictException ex) {
			return false;
		}
	}

	private <T> List<T> race(Callable<T> first, Callable<T> second) throws Exception {
		CountDownLatch start = new CountDownLatch(1);
		List<Future<T>> futures = new ArrayList<>();
		for (Callable<T> task : List.of(first, second)) {
			futures.add(executor.submit(() -> {
				start.await();
				return task.call();
			}));
		}
		start.countDown();
		List<T> results = new ArrayList<>();
		for (Future<T> future : futures) {
			results.add(future.get(30, TimeUnit.SECONDS));
		}
		return results;
	}

	private void assertExactlyOneActiveBooking(long slotId) {
		assertEquals(1, jdbc.queryForObject(
				"SELECT COUNT(*) FROM appointments WHERE slot_id = ? AND status = 'BOOKED'", Integer.class, slotId),
				"one active appointment for slot " + slotId + " in PostgreSQL");
		assertFalse(Boolean.TRUE.equals(jdbc.queryForObject(
				"SELECT is_available FROM availability_slots WHERE slot_id = ?", Boolean.class, slotId)),
				"slot " + slotId + " is marked unavailable");
	}

	private AppUser customer(String email) {
		return userRepository.findByEmail(email).orElseThrow();
	}
}
