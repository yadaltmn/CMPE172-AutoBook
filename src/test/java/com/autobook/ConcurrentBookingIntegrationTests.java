package com.autobook;

import com.autobook.dto.BookingRequest;
import com.autobook.exception.BookingConflictException;
import com.autobook.model.AppUser;
import com.autobook.repository.UserRepository;
import com.autobook.service.BookingService;
import java.net.CookieManager;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Proves that simultaneous booking attempts for the same slot produce exactly one appointment.
 * This class commits real transactions, so it runs against its own in-memory database.
 */
@SpringBootTest(
		webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
		properties = "spring.datasource.url=jdbc:h2:mem:autobook_concurrency;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE;LOCK_TIMEOUT=10000")
class ConcurrentBookingIntegrationTests {

	private static final String PASSWORD = "password123";
	private static final Pattern TOKEN = Pattern.compile("\"token\"\\s*:\\s*\"([^\"]+)\"");

	@LocalServerPort
	private int port;

	@Autowired
	private BookingService bookingService;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Test
	@Timeout(value = 30, unit = TimeUnit.SECONDS)
	void twoCustomersBookingTheSameSlotOverHttpGetOneSuccessAndOneConflict() throws Exception {
		long slotId = createFutureSlot(5);
		HttpClient jada = loggedInClient("jada.nguyen@example.com");
		HttpClient alex = loggedInClient("alex.rivera@example.com");

		List<Callable<Integer>> attempts = List.of(
				() -> postBooking(jada, slotId),
				() -> postBooking(alex, slotId));
		List<Integer> statuses = runSimultaneously(attempts);

		assertEquals(1, statuses.stream().filter(code -> code == 201).count(), "exactly one booking succeeds: " + statuses);
		assertEquals(1, statuses.stream().filter(code -> code == 409).count(), "exactly one booking conflicts: " + statuses);
		assertConsistent(slotId);
	}

	@Test
	@Timeout(value = 30, unit = TimeUnit.SECONDS)
	void manyConcurrentBookingAttemptsProduceExactlyOneAppointment() throws Exception {
		long slotId = createFutureSlot(6);
		int customers = 12;
		List<AppUser> users = createCustomers(customers, slotId);

		List<Callable<Integer>> attempts = new ArrayList<>();
		for (AppUser user : users) {
			attempts.add(() -> {
				try {
					bookingService.book(user, new BookingRequest(slotId, null));
					return 201;
				} catch (BookingConflictException ex) {
					return 409;
				}
			});
		}
		List<Integer> outcomes = runSimultaneously(attempts);

		assertEquals(1, outcomes.stream().filter(code -> code == 201).count(), "exactly one booking succeeds: " + outcomes);
		assertEquals(customers - 1, outcomes.stream().filter(code -> code == 409).count(), "all others conflict: " + outcomes);
		assertConsistent(slotId);
	}

	private void assertConsistent(long slotId) {
		Integer active = jdbcTemplate.queryForObject(
				"SELECT COUNT(*) FROM appointments WHERE slot_id = ? AND status = 'BOOKED'", Integer.class, slotId);
		Boolean available = jdbcTemplate.queryForObject(
				"SELECT is_available FROM availability_slots WHERE slot_id = ?", Boolean.class, slotId);
		assertEquals(1, active, "exactly one active appointment exists for the slot");
		assertFalse(available, "the slot is marked unavailable");
	}

	private List<Integer> runSimultaneously(List<Callable<Integer>> tasks) throws Exception {
		ExecutorService executor = Executors.newFixedThreadPool(tasks.size());
		CountDownLatch ready = new CountDownLatch(tasks.size());
		CountDownLatch start = new CountDownLatch(1);
		try {
			List<Future<Integer>> futures = new ArrayList<>();
			for (Callable<Integer> task : tasks) {
				futures.add(executor.submit(() -> {
					ready.countDown();
					start.await();
					return task.call();
				}));
			}
			assertTrue(ready.await(10, TimeUnit.SECONDS), "all threads ready");
			start.countDown();

			List<Integer> results = new ArrayList<>();
			for (Future<Integer> future : futures) {
				results.add(future.get(20, TimeUnit.SECONDS));
			}
			return results;
		} finally {
			executor.shutdownNow();
		}
	}

	private long createFutureSlot(int daysAhead) {
		LocalDateTime start = LocalDateTime.now().plusDays(daysAhead).withHour(8).withMinute(0).withSecond(0).withNano(0);
		jdbcTemplate.update("""
				INSERT INTO availability_slots (provider_id, service_id, start_time, end_time, is_available)
				VALUES (1, 1, ?, ?, TRUE)
				""", Timestamp.valueOf(start), Timestamp.valueOf(start.plusMinutes(45)));
		Long slotId = jdbcTemplate.queryForObject("SELECT MAX(slot_id) FROM availability_slots", Long.class);
		return slotId;
	}

	private List<AppUser> createCustomers(int count, long uniqueSuffix) {
		List<AppUser> users = new ArrayList<>();
		String hash = jdbcTemplate.queryForObject("SELECT password FROM users WHERE user_id = 1", String.class);
		for (int i = 0; i < count; i++) {
			String email = "racer" + uniqueSuffix + "-" + i + "@example.com";
			jdbcTemplate.update("""
					INSERT INTO users (first_name, last_name, email, password, role)
					VALUES ('Race', ?, ?, ?, 'CUSTOMER')
					""", "Tester" + i, email, hash);
			users.add(userRepository.findByEmail(email).orElseThrow());
		}
		return users;
	}

	private HttpClient loggedInClient(String email) throws Exception {
		HttpClient client = HttpClient.newBuilder()
				.cookieHandler(new CookieManager())
				.followRedirects(HttpClient.Redirect.NEVER)
				.connectTimeout(Duration.ofSeconds(5))
				.build();

		String loginForm = "username=" + encode(email) + "&password=" + encode(PASSWORD) + "&_csrf=" + encode(csrfToken(client));
		HttpResponse<String> login = client.send(HttpRequest.newBuilder(url("/login"))
				.header("Content-Type", "application/x-www-form-urlencoded")
				.POST(HttpRequest.BodyPublishers.ofString(loginForm))
				.build(), HttpResponse.BodyHandlers.ofString());
		assertEquals(302, login.statusCode());
		assertFalse(login.headers().firstValue("Location").orElse("").contains("error"), "login succeeded for " + email);
		return client;
	}

	private int postBooking(HttpClient client, long slotId) throws Exception {
		String token = csrfToken(client);
		HttpResponse<String> response = client.send(HttpRequest.newBuilder(url("/api/appointments"))
				.header("Content-Type", "application/json")
				.header("X-CSRF-TOKEN", token)
				.timeout(Duration.ofSeconds(15))
				.POST(HttpRequest.BodyPublishers.ofString("{\"slotId\":" + slotId + "}"))
				.build(), HttpResponse.BodyHandlers.ofString());
		return response.statusCode();
	}

	private String csrfToken(HttpClient client) throws Exception {
		HttpResponse<String> response = client.send(HttpRequest.newBuilder(url("/api/csrf")).GET().build(),
				HttpResponse.BodyHandlers.ofString());
		Matcher matcher = TOKEN.matcher(response.body());
		assertTrue(matcher.find(), "csrf token returned");
		return matcher.group(1);
	}

	private URI url(String path) {
		return URI.create("http://localhost:" + port + path);
	}

	private static String encode(String value) {
		return URLEncoder.encode(value, StandardCharsets.UTF_8);
	}
}
