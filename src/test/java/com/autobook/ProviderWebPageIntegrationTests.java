package com.autobook;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.context.support.WithUserDetails;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ProviderWebPageIntegrationTests {

	private static final String DOWNTOWN = "service@downtownautocare.example.com";

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Test
	@WithUserDetails(DOWNTOWN)
	void providerDashboardShowsOwnScheduleSummary() throws Exception {
		mockMvc.perform(get("/dashboard")).andExpect(redirectedUrl("/provider/dashboard"));
		mockMvc.perform(get("/provider/dashboard"))
				.andExpect(status().isOk())
				.andExpect(content().string(containsString("Downtown Auto Care")))
				.andExpect(content().string(containsString("Alex Rivera")))
				.andExpect(content().string(not(containsString("Westside Tire"))));
	}

	@Test
	@WithUserDetails(DOWNTOWN)
	void providerAddsAvailabilityThroughTheForm() throws Exception {
		String date = LocalDate.now().plusDays(8).toString();
		mockMvc.perform(post("/provider/availability").with(csrf())
						.param("serviceId", "2").param("date", date)
						.param("startTime", "13:00").param("endTime", "14:00"))
				.andExpect(redirectedUrl("/provider/availability"))
				.andExpect(flash().attribute("successMessage", "Added Vehicle Inspection availability."));

		Integer created = jdbcTemplate.queryForObject(
				"SELECT COUNT(*) FROM availability_slots WHERE provider_id = 1 AND CAST(start_time AS DATE) = ?",
				Integer.class, LocalDate.parse(date));
		assertEquals(1, created);
	}

	@Test
	@WithUserDetails(DOWNTOWN)
	void availabilityFormShowsValidationMessages() throws Exception {
		mockMvc.perform(post("/provider/availability").with(csrf()).param("date", LocalDate.now().plusDays(8).toString()))
				.andExpect(status().isOk())
				.andExpect(model().attributeHasFieldErrors("slotForm", "serviceId", "startTime", "endTime"))
				.andExpect(content().string(containsString("Please choose a service.")));

		mockMvc.perform(post("/provider/availability").with(csrf())
						.param("serviceId", "1").param("date", LocalDate.now().plusDays(1).toString())
						.param("startTime", "09:15").param("endTime", "10:00"))
				.andExpect(status().isOk())
				.andExpect(content().string(containsString("This time overlaps another slot on your schedule.")));
	}

	@Test
	@WithUserDetails(DOWNTOWN)
	void providerRemovesSlotAndCannotRemoveOthers() throws Exception {
		mockMvc.perform(post("/provider/availability/2/delete").with(csrf()))
				.andExpect(redirectedUrl("/provider/availability"))
				.andExpect(flash().attribute("successMessage", "The slot was removed from your schedule."));

		mockMvc.perform(post("/provider/availability/9/delete").with(csrf()))
				.andExpect(flash().attribute("errorMessage", "This slot is booked or closed and cannot be removed."));

		mockMvc.perform(post("/provider/availability/4/delete").with(csrf()))
				.andExpect(status().isForbidden());
	}

	@Test
	@WithUserDetails(DOWNTOWN)
	void providerBookingsPageListsTheirAppointments() throws Exception {
		mockMvc.perform(get("/provider/appointments"))
				.andExpect(status().isOk())
				.andExpect(content().string(containsString("alex.rivera@example.com")))
				.andExpect(content().string(not(containsString("Tire Service"))));
	}

	@Test
	@WithUserDetails("jada.nguyen@example.com")
	void customerCannotOpenProviderPages() throws Exception {
		mockMvc.perform(get("/provider/availability")).andExpect(status().isForbidden());
	}
}
