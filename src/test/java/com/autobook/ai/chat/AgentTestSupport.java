package com.autobook.ai.chat;

import com.jayway.jsonpath.JsonPath;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * Shared helpers for the assistant tests: chat as a given user over HTTP, and create future slots.
 */
final class AgentTestSupport {

	static final String JADA = "jada.nguyen@example.com";
	static final String ALEX = "alex.rivera@example.com";
	static final String ADMIN = "morgan.lee@example.com";
	static final String PROVIDER = "service@downtownautocare.example.com";

	private static final AtomicInteger SLOT_SEQUENCE = new AtomicInteger();

	private AgentTestSupport() {
	}

	static ResultActions chat(MockMvc mockMvc, UserDetailsService users, String email, String conversationId,
	                          String message) throws Exception {
		String body = conversationId == null
				? "{\"message\":" + quote(message) + "}"
				: "{\"conversationId\":" + quote(conversationId) + ",\"message\":" + quote(message) + "}";
		return mockMvc.perform(post("/api/ai/chat").with(user(users.loadUserByUsername(email))).with(csrf())
				.contentType(MediaType.APPLICATION_JSON).content(body));
	}

	static String read(ResultActions result, String path) throws Exception {
		Object value = JsonPath.read(result.andReturn().getResponse().getContentAsString(), path);
		return value == null ? null : value.toString();
	}

	static String quote(String text) {
		return "\"" + text.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
	}

	/** Creates an open slot far in the future so it never collides with seed data or other tests. */
	static long createSlot(JdbcTemplate jdbc, long providerId, long serviceId, LocalDateTime start) {
		jdbc.update("""
				INSERT INTO availability_slots (provider_id, service_id, start_time, end_time, is_available)
				VALUES (?, ?, ?, ?, TRUE)
				""", providerId, serviceId, Timestamp.valueOf(start), Timestamp.valueOf(start.plusMinutes(45)));
		return jdbc.queryForObject("SELECT MAX(slot_id) FROM availability_slots", Long.class);
	}

	/** A unique future start time: day 20 + n at 10:00 local time. */
	static LocalDateTime uniqueFutureStart() {
		return LocalDate.now().plusDays(20 + SLOT_SEQUENCE.incrementAndGet()).atTime(10, 0);
	}
}
