package com.autobook;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.context.support.WithUserDetails;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.startsWith;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class BookingIntegrationTests {

	private static final String CUSTOMER = "jada.nguyen@example.com";
	private static final String PROVIDER = "service@downtownautocare.example.com";

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Test
	@WithUserDetails(CUSTOMER)
	void customerBooksAvailableSlot() throws Exception {
		book("{\"slotId\":1,\"serviceId\":1}")
				.andExpect(status().isCreated())
				.andExpect(header().string("Location", startsWith("/api/appointments/")))
				.andExpect(jsonPath("$.slotId", is(1)))
				.andExpect(jsonPath("$.customerEmail", is(CUSTOMER)))
				.andExpect(jsonPath("$.providerName", is("Downtown Auto Care")))
				.andExpect(jsonPath("$.serviceName", is("Oil Change")))
				.andExpect(jsonPath("$.status", is("BOOKED")));

		Boolean available = jdbcTemplate.queryForObject(
				"SELECT is_available FROM availability_slots WHERE slot_id = 1", Boolean.class);
		assertFalse(available);
		assertEquals(1, activeBookings(1));
	}

	@Test
	@WithUserDetails(CUSTOMER)
	void bookingIgnoresClientSuppliedUserId() throws Exception {
		book("{\"slotId\":2,\"userId\":2}")
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.customerId", is(1)))
				.andExpect(jsonPath("$.customerEmail", is(CUSTOMER)));
	}

	@Test
	@WithUserDetails(CUSTOMER)
	void bookingMissingSlotReturns404() throws Exception {
		book("{\"slotId\":9999}")
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.status", is(404)));
	}

	@Test
	@WithUserDetails(CUSTOMER)
	void bookingUnavailableSlotReturns409() throws Exception {
		book("{\"slotId\":6}")
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.message", is("Sorry, this appointment slot has already been booked.")));
		assertEquals(1, activeBookings(6));
	}

	@Test
	@WithUserDetails(CUSTOMER)
	void bookingPastSlotReturns400() throws Exception {
		LocalDateTime start = LocalDateTime.now().minusHours(3).withNano(0);
		jdbcTemplate.update("""
				INSERT INTO availability_slots (provider_id, service_id, start_time, end_time, is_available)
				VALUES (1, 1, ?, ?, TRUE)
				""", Timestamp.valueOf(start), Timestamp.valueOf(start.plusMinutes(45)));
		Long pastSlotId = jdbcTemplate.queryForObject("SELECT MAX(slot_id) FROM availability_slots", Long.class);

		book("{\"slotId\":" + pastSlotId + "}")
				.andExpect(status().isBadRequest());
		assertEquals(0, activeBookings(pastSlotId));
	}

	@Test
	@WithUserDetails(CUSTOMER)
	void bookingWithMismatchedServiceReturns400() throws Exception {
		book("{\"slotId\":1,\"serviceId\":3}")
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message", is("The selected service does not match this appointment slot.")));
	}

	@Test
	@WithUserDetails(CUSTOMER)
	void bookingValidatesRequestBody() throws Exception {
		book("{}").andExpect(status().isBadRequest());
		book("{\"slotId\":-4}").andExpect(status().isBadRequest());
		book("not json").andExpect(status().isBadRequest());
	}

	@Test
	@WithUserDetails(CUSTOMER)
	void bookingSameSlotTwiceReturnsConflict() throws Exception {
		book("{\"slotId\":3}").andExpect(status().isCreated());
		book("{\"slotId\":3}").andExpect(status().isConflict());
		assertEquals(1, activeBookings(3));
	}

	@Test
	void unauthenticatedBookingReturns401() throws Exception {
		book("{\"slotId\":1}").andExpect(status().isUnauthorized());
		assertEquals(0, activeBookings(1));
	}

	@Test
	@WithUserDetails(PROVIDER)
	void providerCannotBook() throws Exception {
		book("{\"slotId\":1}").andExpect(status().isForbidden());
		assertEquals(0, activeBookings(1));
	}

	@Test
	@WithUserDetails(CUSTOMER)
	void bookingWithoutCsrfTokenIsRejected() throws Exception {
		mockMvc.perform(post("/api/appointments")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"slotId\":1}"))
				.andExpect(status().isForbidden());
		assertEquals(0, activeBookings(1));
	}

	private ResultActions book(String json) throws Exception {
		return mockMvc.perform(post("/api/appointments").with(csrf())
				.contentType(MediaType.APPLICATION_JSON)
				.content(json));
	}

	private int activeBookings(long slotId) {
		Integer count = jdbcTemplate.queryForObject(
				"SELECT COUNT(*) FROM appointments WHERE slot_id = ? AND status = 'BOOKED'", Integer.class, slotId);
		return count == null ? 0 : count;
	}
}
