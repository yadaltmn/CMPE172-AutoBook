package com.autobook;

import java.sql.Timestamp;
import java.time.LocalDate;
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

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ProviderAvailabilityIntegrationTests {

	private static final String DOWNTOWN = "service@downtownautocare.example.com";
	private static final String WESTSIDE = "hello@westsidetire.example.com";
	private static final String CUSTOMER = "jada.nguyen@example.com";

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Test
	@WithUserDetails(DOWNTOWN)
	void providerListsOnlyTheirOwnSlotsWithBookingState() throws Exception {
		mockMvc.perform(get("/api/provider/slots"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$", hasSize(5)))
				.andExpect(jsonPath("$[*].slotId", containsInAnyOrder(1, 2, 3, 7, 9)))
				.andExpect(jsonPath("$[?(@.slotId == 9)].state", containsInAnyOrder("BOOKED")))
				.andExpect(jsonPath("$[?(@.slotId == 9)].customerName", containsInAnyOrder("Alex Rivera")))
				.andExpect(jsonPath("$[?(@.slotId == 7)].state", containsInAnyOrder("COMPLETED")))
				.andExpect(jsonPath("$[?(@.slotId == 1)].state", containsInAnyOrder("AVAILABLE")));
	}

	@Test
	@WithUserDetails(DOWNTOWN)
	void providerCreatesAvailabilityThatCustomersCanFind() throws Exception {
		createSlot(1, at(6, "09:00"), at(6, "09:45"))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.state", is("AVAILABLE")))
				.andExpect(jsonPath("$.serviceName", is("Oil Change")));

		mockMvc.perform(get("/api/slots").param("providerId", "1"))
				.andExpect(jsonPath("$.totalElements", is(4)));
	}

	@Test
	@WithUserDetails(DOWNTOWN)
	void invalidTimeRangesAreRejected() throws Exception {
		createSlot(1, at(6, "10:00"), at(6, "09:00")).andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message", is("The start time must be before the end time.")));
		createSlot(1, at(6, "10:00"), at(6, "10:00")).andExpect(status().isBadRequest());
		createSlot(1, at(-1, "10:00"), at(-1, "11:00")).andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message", is("Availability must be scheduled in the future.")));
		createSlot(3, at(6, "10:00"), at(6, "10:30")).andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message", is("Vehicle Diagnostics needs at least 90 minutes.")));
		createSlot(99, at(6, "10:00"), at(6, "11:00")).andExpect(status().isBadRequest());
		createSlot(1, at(6, "06:00"), at(6, "18:00")).andExpect(status().isBadRequest());

		mockMvc.perform(post("/api/provider/slots").with(csrf())
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"serviceId\":1}"))
				.andExpect(status().isBadRequest());
	}

	@Test
	@WithUserDetails(DOWNTOWN)
	void overlappingAvailabilityIsRejected() throws Exception {
		// Seed slot 1 runs 09:00-09:45 tomorrow.
		createSlot(1, at(1, "09:30"), at(1, "10:15"))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.message", is("This time overlaps another slot on your schedule.")));
		createSlot(4, at(1, "08:00"), at(1, "12:00"))
				.andExpect(status().isConflict());

		// Back-to-back slots do not overlap.
		createSlot(1, at(1, "09:45"), at(1, "10:30"))
				.andExpect(status().isCreated());
	}

	@Test
	@WithUserDetails(WESTSIDE)
	void differentProvidersMayUseTheSameTime() throws Exception {
		createSlot(1, at(1, "09:00"), at(1, "09:45"))
				.andExpect(status().isCreated());
	}

	@Test
	@WithUserDetails(DOWNTOWN)
	void providerRemovesOpenSlot() throws Exception {
		mockMvc.perform(delete("/api/provider/slots/2").with(csrf()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.outcome", is("DELETED")));

		Integer remaining = jdbcTemplate.queryForObject(
				"SELECT COUNT(*) FROM availability_slots WHERE slot_id = 2", Integer.class);
		assertEquals(0, remaining);
	}

	@Test
	@WithUserDetails(DOWNTOWN)
	void bookedSlotCannotBeRemoved() throws Exception {
		mockMvc.perform(delete("/api/provider/slots/9").with(csrf()))
				.andExpect(status().isConflict());
		assertEquals(1, activeBookings(9));
	}

	@Test
	@WithUserDetails(DOWNTOWN)
	void providerCannotChangeAnotherProvidersSlots() throws Exception {
		mockMvc.perform(delete("/api/provider/slots/4").with(csrf()))
				.andExpect(status().isForbidden());
		Integer remaining = jdbcTemplate.queryForObject(
				"SELECT COUNT(*) FROM availability_slots WHERE slot_id = 4", Integer.class);
		assertEquals(1, remaining);

		mockMvc.perform(delete("/api/provider/slots/9999").with(csrf()))
				.andExpect(status().isNotFound());
	}

	@Test
	@WithUserDetails(WESTSIDE)
	void slotWithCancelledHistoryIsClosedInsteadOfDeleted() throws Exception {
		jdbcTemplate.update("UPDATE appointments SET status = 'CANCELLED' WHERE appointment_id = 1");
		jdbcTemplate.update("UPDATE availability_slots SET is_available = TRUE WHERE slot_id = 6");

		mockMvc.perform(delete("/api/provider/slots/6").with(csrf()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.outcome", is("CLOSED")));

		Boolean available = jdbcTemplate.queryForObject(
				"SELECT is_available FROM availability_slots WHERE slot_id = 6", Boolean.class);
		assertEquals(Boolean.FALSE, available);
		Integer history = jdbcTemplate.queryForObject(
				"SELECT COUNT(*) FROM appointments WHERE slot_id = 6", Integer.class);
		assertEquals(1, history);
	}

	@Test
	@WithUserDetails(DOWNTOWN)
	void providerSeesOnlyAppointmentsBookedWithThem() throws Exception {
		mockMvc.perform(get("/api/provider/appointments"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[*].appointmentId", containsInAnyOrder(2, 4)))
				.andExpect(jsonPath("$[*].providerName", containsInAnyOrder("Downtown Auto Care", "Downtown Auto Care")));
	}

	@Test
	@WithUserDetails(DOWNTOWN)
	void providerMarksPastAppointmentCompleted() throws Exception {
		LocalDateTime start = LocalDateTime.now().minusDays(1).withNano(0);
		jdbcTemplate.update("""
				INSERT INTO availability_slots (provider_id, service_id, start_time, end_time, is_available)
				VALUES (1, 1, ?, ?, FALSE)
				""", Timestamp.valueOf(start), Timestamp.valueOf(start.plusMinutes(45)));
		Long slotId = jdbcTemplate.queryForObject("SELECT MAX(slot_id) FROM availability_slots", Long.class);
		jdbcTemplate.update("""
				INSERT INTO appointments (user_id, provider_id, service_id, slot_id, status)
				VALUES (2, 1, 1, ?, 'BOOKED')
				""", slotId);
		Long appointmentId = jdbcTemplate.queryForObject("SELECT MAX(appointment_id) FROM appointments", Long.class);

		mockMvc.perform(post("/api/provider/appointments/" + appointmentId + "/complete").with(csrf()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status", is("COMPLETED")));

		mockMvc.perform(post("/api/provider/appointments/4/complete").with(csrf()))
				.andExpect(status().isBadRequest());
		mockMvc.perform(post("/api/provider/appointments/1/complete").with(csrf()))
				.andExpect(status().isForbidden());
	}

	@Test
	@WithUserDetails(CUSTOMER)
	void customerCannotManageAvailability() throws Exception {
		createSlot(1, at(6, "09:00"), at(6, "09:45")).andExpect(status().isForbidden());
		mockMvc.perform(delete("/api/provider/slots/1").with(csrf())).andExpect(status().isForbidden());
	}

	private ResultActions createSlot(long serviceId, String start, String end) throws Exception {
		return mockMvc.perform(post("/api/provider/slots").with(csrf())
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"serviceId\":" + serviceId + ",\"startTime\":\"" + start + "\",\"endTime\":\"" + end + "\"}"));
	}

	private static String at(int daysFromToday, String time) {
		return LocalDate.now().plusDays(daysFromToday) + "T" + time + ":00";
	}

	private int activeBookings(long slotId) {
		Integer count = jdbcTemplate.queryForObject(
				"SELECT COUNT(*) FROM appointments WHERE slot_id = ? AND status = 'BOOKED'", Integer.class, slotId);
		return count == null ? 0 : count;
	}
}
