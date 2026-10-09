package com.autobook;

import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class SlotSearchIntegrationTests {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Test
	void searchReturnsOnlyFutureAvailableSlotsSortedByStartTime() throws Exception {
		LocalDateTime past = LocalDateTime.now().minusDays(2).withNano(0);
		insertSlot(1, 1, past, past.plusMinutes(45), true);

		mockMvc.perform(get("/api/slots"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.totalElements", is(5)))
				.andExpect(jsonPath("$.content[0].slotId", is(1)))
				.andExpect(jsonPath("$.content[*].slotId", not(hasItem(6))))
				.andExpect(jsonPath("$.content[*].available", everyItem(is(true))));
	}

	@Test
	void searchFiltersByProvider() throws Exception {
		mockMvc.perform(get("/api/slots").param("providerId", "2"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.totalElements", is(2)))
				.andExpect(jsonPath("$.content[*].providerName", everyItem(is("Westside Tire and Inspection"))));
	}

	@Test
	void searchFiltersByServiceAndDate() throws Exception {
		mockMvc.perform(get("/api/slots").param("serviceId", "2"))
				.andExpect(jsonPath("$.totalElements", is(2)))
				.andExpect(jsonPath("$.content[*].serviceName", everyItem(is("Vehicle Inspection"))));

		LocalDate dayThree = LocalDate.now().plusDays(3);
		mockMvc.perform(get("/api/slots").param("date", dayThree.toString()))
				.andExpect(jsonPath("$.totalElements", is(2)))
				.andExpect(jsonPath("$.content[*].startTime", everyItem(startsWith(dayThree.toString()))));

		mockMvc.perform(get("/api/slots").param("serviceId", "2").param("date", dayThree.toString()))
				.andExpect(jsonPath("$.totalElements", is(1)))
				.andExpect(jsonPath("$.content[0].slotId", is(5)));
	}

	@Test
	void searchPaginatesWithLimitAndOffset() throws Exception {
		mockMvc.perform(get("/api/slots").param("size", "2").param("page", "0"))
				.andExpect(jsonPath("$.content", hasSize(2)))
				.andExpect(jsonPath("$.content[0].slotId", is(1)))
				.andExpect(jsonPath("$.totalElements", is(5)))
				.andExpect(jsonPath("$.totalPages", is(3)));

		mockMvc.perform(get("/api/slots").param("size", "2").param("page", "2"))
				.andExpect(jsonPath("$.content", hasSize(1)))
				.andExpect(jsonPath("$.content[0].slotId", is(5)));
	}

	@Test
	void searchRejectsInvalidParameters() throws Exception {
		mockMvc.perform(get("/api/slots").param("page", "-1"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.status", is(400)));
		mockMvc.perform(get("/api/slots").param("size", "500"))
				.andExpect(status().isBadRequest());
		mockMvc.perform(get("/api/slots").param("date", "not-a-date"))
				.andExpect(status().isBadRequest());
		mockMvc.perform(get("/api/slots").param("providerId", "abc"))
				.andExpect(status().isBadRequest());
	}

	@Test
	void slotDetailsIncludeServiceInformation() throws Exception {
		mockMvc.perform(get("/api/slots/3"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.serviceName", is("Vehicle Diagnostics")))
				.andExpect(jsonPath("$.durationMinutes", is(90)));

		mockMvc.perform(get("/api/slots/9999"))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.status", is(404)));
	}

	@Test
	void providerAndServiceListsArePublic() throws Exception {
		mockMvc.perform(get("/api/providers")).andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(2)));
		mockMvc.perform(get("/api/services")).andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(4)));
	}

	private void insertSlot(long providerId, long serviceId, LocalDateTime start, LocalDateTime end, boolean available) {
		jdbcTemplate.update("""
				INSERT INTO availability_slots (provider_id, service_id, start_time, end_time, is_available)
				VALUES (?, ?, ?, ?, ?)
				""", providerId, serviceId, Timestamp.valueOf(start), Timestamp.valueOf(end), available);
	}
}
