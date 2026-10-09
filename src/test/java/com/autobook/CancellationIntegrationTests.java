package com.autobook;

import com.autobook.model.AppointmentStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.test.context.support.WithUserDetails;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class CancellationIntegrationTests {

	private static final String JADA = "jada.nguyen@example.com";
	private static final String ALEX = "alex.rivera@example.com";
	private static final String PROVIDER = "service@downtownautocare.example.com";

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Autowired
	private UserDetailsService userDetailsService;

	@Test
	@WithUserDetails(JADA)
	void customerSeesOnlyTheirOwnUpcomingAndPastAppointments() throws Exception {
		mockMvc.perform(get("/api/appointments/me"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.upcoming", hasSize(1)))
				.andExpect(jsonPath("$.upcoming[0].appointmentId", is(1)))
				.andExpect(jsonPath("$.upcoming[0].status", is("BOOKED")))
				.andExpect(jsonPath("$.history", hasSize(2)))
				.andExpect(jsonPath("$.history[*].status", containsInAnyOrder("COMPLETED", "CANCELLED")))
				.andExpect(jsonPath("$..customerEmail", everyItem(is(JADA))));
	}

	@Test
	@WithUserDetails(JADA)
	void customerCannotViewAnotherCustomersAppointment() throws Exception {
		mockMvc.perform(get("/api/appointments/1")).andExpect(status().isOk());
		mockMvc.perform(get("/api/appointments/4"))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.status", is(403)));
		mockMvc.perform(get("/api/appointments/999")).andExpect(status().isNotFound());
	}

	@Test
	@WithUserDetails(JADA)
	void customerCancelsOwnAppointmentAndSlotIsReopened() throws Exception {
		mockMvc.perform(post("/api/appointments/1/cancel").with(csrf()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status", is("CANCELLED")));

		assertEquals("CANCELLED", statusOf(1));
		assertTrue(slotAvailable(6), "slot returns to the bookable pool");
		assertEquals(0, activeBookings(6));
		Integer rows = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM appointments WHERE slot_id = 6", Integer.class);
		assertEquals(1, rows, "the cancelled appointment is kept as history");
	}

	@Test
	@WithUserDetails(JADA)
	void cancelledSlotCanBeRebookedByAnotherCustomer() throws Exception {
		mockMvc.perform(post("/api/appointments/1/cancel").with(csrf())).andExpect(status().isOk());

		mockMvc.perform(post("/api/appointments").with(csrf())
						.with(user(userDetailsService.loadUserByUsername(ALEX)))
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"slotId\":6}"))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.customerEmail", is(ALEX)));

		assertEquals(1, activeBookings(6));
		Integer rows = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM appointments WHERE slot_id = 6", Integer.class);
		assertEquals(2, rows, "history row plus the new booking");
	}

	@Test
	@WithUserDetails(JADA)
	void customerCannotCancelAnotherCustomersAppointment() throws Exception {
		mockMvc.perform(post("/api/appointments/4/cancel").with(csrf()))
				.andExpect(status().isForbidden());

		assertEquals("BOOKED", statusOf(4));
		assertEquals(1, activeBookings(9));
	}

	@Test
	@WithUserDetails(JADA)
	void onlyBookedFutureAppointmentsCanBeCancelled() throws Exception {
		mockMvc.perform(post("/api/appointments/2/cancel").with(csrf()))
				.andExpect(status().isConflict());
		mockMvc.perform(post("/api/appointments/3/cancel").with(csrf()))
				.andExpect(status().isConflict());

		mockMvc.perform(post("/api/appointments/1/cancel").with(csrf())).andExpect(status().isOk());
		mockMvc.perform(post("/api/appointments/1/cancel").with(csrf()))
				.andExpect(status().isConflict());

		assertEquals("COMPLETED", statusOf(2));
		assertEquals("CANCELLED", statusOf(3));
	}

	@Test
	@WithUserDetails(PROVIDER)
	void providerCannotCancelThroughCustomerApi() throws Exception {
		mockMvc.perform(post("/api/appointments/1/cancel").with(csrf()))
				.andExpect(status().isForbidden());
		assertEquals("BOOKED", statusOf(1));
	}

	@Test
	void unauthenticatedCancellationIsRejected() throws Exception {
		mockMvc.perform(post("/api/appointments/1/cancel").with(csrf()))
				.andExpect(status().isUnauthorized());
		assertEquals("BOOKED", statusOf(1));
	}

	@Test
	void databaseAllowsHistoryButOnlyOneActiveAppointmentPerSlot() {
		jdbcTemplate.update("UPDATE appointments SET status = 'CANCELLED' WHERE appointment_id = 1");
		jdbcTemplate.update("""
				INSERT INTO appointments (user_id, provider_id, service_id, slot_id, status)
				VALUES (2, 2, 1, 6, ?)
				""", AppointmentStatus.BOOKED.name());

		assertThrows(DataIntegrityViolationException.class, () -> jdbcTemplate.update("""
				INSERT INTO appointments (user_id, provider_id, service_id, slot_id, status)
				VALUES (1, 2, 1, 6, ?)
				""", AppointmentStatus.BOOKED.name()));
	}

	private String statusOf(long appointmentId) {
		return jdbcTemplate.queryForObject("SELECT status FROM appointments WHERE appointment_id = ?", String.class, appointmentId);
	}

	private boolean slotAvailable(long slotId) {
		return Boolean.TRUE.equals(jdbcTemplate.queryForObject(
				"SELECT is_available FROM availability_slots WHERE slot_id = ?", Boolean.class, slotId));
	}

	private int activeBookings(long slotId) {
		Integer count = jdbcTemplate.queryForObject(
				"SELECT COUNT(*) FROM appointments WHERE slot_id = ? AND status = 'BOOKED'", Integer.class, slotId);
		return count == null ? 0 : count;
	}
}
