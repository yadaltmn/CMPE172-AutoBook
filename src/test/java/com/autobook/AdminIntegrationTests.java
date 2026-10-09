package com.autobook;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.context.support.WithUserDetails;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AdminIntegrationTests {

	private static final String ADMIN = "morgan.lee@example.com";

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Test
	@WithUserDetails(ADMIN)
	void adminDashboardShowsProvidersUsersAndAppointments() throws Exception {
		mockMvc.perform(get("/dashboard")).andExpect(redirectedUrl("/admin/dashboard"));
		mockMvc.perform(get("/admin/dashboard"))
				.andExpect(status().isOk())
				.andExpect(content().string(containsString("Westside Tire and Inspection")))
				.andExpect(content().string(containsString("morgan.lee@example.com")))
				.andExpect(content().string(containsString("Alex Rivera")));
	}

	@Test
	@WithUserDetails(ADMIN)
	void adminCanSeeAllAppointmentsThroughApi() throws Exception {
		mockMvc.perform(get("/api/admin/appointments"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$", hasSize(4)));
		mockMvc.perform(get("/api/admin/users"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$", hasSize(5)));
	}

	@Test
	@WithUserDetails(ADMIN)
	void adminEditsProviderThroughTheWebsite() throws Exception {
		mockMvc.perform(get("/admin/providers/2/edit"))
				.andExpect(status().isOk())
				.andExpect(content().string(containsString("Westside Tire and Inspection")));

		mockMvc.perform(post("/admin/providers/2").with(csrf())
						.param("name", "Westside Tire & Alignment").param("phone", "408-555-0177"))
				.andExpect(redirectedUrl("/admin/dashboard"));

		String name = jdbcTemplate.queryForObject("SELECT name FROM providers WHERE provider_id = 2", String.class);
		assertEquals("Westside Tire & Alignment", name);
	}

	@Test
	@WithUserDetails(ADMIN)
	void adminProviderFormValidatesInput() throws Exception {
		mockMvc.perform(post("/admin/providers/2").with(csrf()).param("name", "").param("phone", "not a phone"))
				.andExpect(status().isOk())
				.andExpect(model().attributeHasFieldErrors("providerForm", "name", "phone"));

		mockMvc.perform(get("/admin/providers/999/edit"))
				.andExpect(status().isNotFound());
	}

	@Test
	@WithUserDetails("service@downtownautocare.example.com")
	void providerCannotOpenAdminPages() throws Exception {
		mockMvc.perform(get("/admin/dashboard")).andExpect(status().isForbidden());
		mockMvc.perform(post("/admin/providers/2").with(csrf()).param("name", "Hijacked").param("phone", ""))
				.andExpect(status().isForbidden());
		String name = jdbcTemplate.queryForObject("SELECT name FROM providers WHERE provider_id = 2", String.class);
		assertEquals("Westside Tire and Inspection", name);
	}
}
