package com.autobook;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class AutobookApplicationTests {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Test
	void contextLoads() {
	}

	@Test
	void seedDataLoads() {
		Integer providerCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM providers", Integer.class);
		Integer serviceCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM services", Integer.class);
		Integer availableSlotCount = jdbcTemplate.queryForObject(
				"SELECT COUNT(*) FROM availability_slots WHERE is_available = TRUE",
				Integer.class
		);

		assertEquals(2, providerCount);
		assertEquals(4, serviceCount);
		assertEquals(5, availableSlotCount);
	}

	@Test
	void homeEndpointReturnsDatabaseBackedSummary() throws Exception {
		mockMvc.perform(get("/"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.applicationName", is("AutoBook")))
				.andExpect(jsonPath("$.providerCount", is(2)))
				.andExpect(jsonPath("$.serviceCount", is(4)))
				.andExpect(jsonPath("$.availableSlotCount", is(5)));
	}

	@Test
	void slotsEndpointReturnsAvailableDatabaseRows() throws Exception {
		mockMvc.perform(get("/slots"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$", hasSize(5)))
				.andExpect(jsonPath("$[0].slotId", is(1)))
				.andExpect(jsonPath("$[0].providerName", is("Downtown Auto Care")))
				.andExpect(jsonPath("$[0].serviceName", is("Oil Change")))
				.andExpect(jsonPath("$[0].available", is(true)));
	}

	@Test
	void appointmentsRejectDuplicateSlotBookings() {
		assertThrows(DataIntegrityViolationException.class, () -> jdbcTemplate.update("""
				INSERT INTO appointments (user_id, provider_id, service_id, slot_id, status)
				VALUES (2, 2, 1, 6, 'BOOKED')
				"""));
	}

}
