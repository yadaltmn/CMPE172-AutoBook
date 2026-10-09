package com.autobook;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.context.support.WithUserDetails;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.forwardedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class CustomerWebPageIntegrationTests {

	private static final String CUSTOMER = "jada.nguyen@example.com";
	private static final String PROVIDER = "service@downtownautocare.example.com";

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Test
	void browserHomePageRendersLandingPageWhileJsonClientsGetSummary() throws Exception {
		mockMvc.perform(get("/").accept(MediaType.TEXT_HTML))
				.andExpect(status().isOk())
				.andExpect(view().name("index"))
				.andExpect(content().string(containsString("VIEW AVAILABLE SLOTS")));

		mockMvc.perform(get("/").accept(MediaType.APPLICATION_JSON))
				.andExpect(status().isOk())
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON));
	}

	@Test
	void loginPageIsPublic() throws Exception {
		mockMvc.perform(get("/login"))
				.andExpect(status().isOk())
				.andExpect(content().string(containsString("name=\"_csrf\"")))
				.andExpect(content().string(containsString("Email address")));
	}

	@Test
	void browsePageShowsFilteredDatabaseSlots() throws Exception {
		mockMvc.perform(get("/browse"))
				.andExpect(status().isOk())
				.andExpect(content().string(containsString("Vehicle Diagnostics")))
				.andExpect(content().string(containsString("Tire Service")));

		mockMvc.perform(get("/browse").param("serviceId", "3"))
				.andExpect(status().isOk())
				.andExpect(content().string(containsString("open slot found")))
				.andExpect(content().string(not(containsString("Tire Service</h2>"))));
	}

	@Test
	@WithUserDetails(CUSTOMER)
	void dashboardRoutesUsersByRole() throws Exception {
		mockMvc.perform(get("/dashboard")).andExpect(redirectedUrl("/customer/dashboard"));
		mockMvc.perform(get("/customer/dashboard"))
				.andExpect(status().isOk())
				.andExpect(content().string(containsString("Welcome back, Jada")));
	}

	@Test
	@WithUserDetails(CUSTOMER)
	void customerCompletesBookingThroughTheWebsite() throws Exception {
		mockMvc.perform(get("/customer/book/1"))
				.andExpect(status().isOk())
				.andExpect(content().string(containsString("Review your booking")))
				.andExpect(content().string(containsString("Downtown Auto Care")));

		MvcResult booking = mockMvc.perform(post("/customer/book").with(csrf())
						.param("slotId", "1").param("serviceId", "1"))
				.andExpect(status().is3xxRedirection())
				.andExpect(redirectedUrlPattern("/customer/appointments/*/confirmation"))
				.andReturn();

		mockMvc.perform(get(booking.getResponse().getRedirectedUrl()))
				.andExpect(status().isOk())
				.andExpect(content().string(containsString("Booking confirmed")))
				.andExpect(content().string(containsString("Oil Change")));

		Integer active = jdbcTemplate.queryForObject(
				"SELECT COUNT(*) FROM appointments WHERE slot_id = 1 AND status = 'BOOKED'", Integer.class);
		assertEquals(1, active);
	}

	@Test
	@WithUserDetails(CUSTOMER)
	void bookingAnUnavailableSlotShowsFriendlyMessage() throws Exception {
		mockMvc.perform(post("/customer/book").with(csrf()).param("slotId", "6"))
				.andExpect(redirectedUrl("/browse"))
				.andExpect(flash().attribute("errorMessage", "Sorry, this appointment slot has already been booked."));

		mockMvc.perform(get("/customer/book/6"))
				.andExpect(redirectedUrl("/browse"));
	}

	@Test
	@WithUserDetails(CUSTOMER)
	void appointmentsPageShowsUpcomingAndHistory() throws Exception {
		mockMvc.perform(get("/customer/appointments"))
				.andExpect(status().isOk())
				.andExpect(content().string(containsString("Upcoming")))
				.andExpect(content().string(containsString("Completed")))
				.andExpect(content().string(containsString("Cancelled")))
				.andExpect(content().string(not(containsString("Alex"))));
	}

	@Test
	@WithUserDetails(CUSTOMER)
	void customerCancelsThroughTheWebsite() throws Exception {
		mockMvc.perform(get("/customer/appointments/1/cancel"))
				.andExpect(status().isOk())
				.andExpect(content().string(containsString("Cancel this appointment?")));

		mockMvc.perform(post("/customer/appointments/1/cancel").with(csrf()))
				.andExpect(redirectedUrl("/customer/appointments"))
				.andExpect(flash().attributeExists("successMessage"));

		String status = jdbcTemplate.queryForObject(
				"SELECT status FROM appointments WHERE appointment_id = 1", String.class);
		assertEquals("CANCELLED", status);
	}

	@Test
	@WithUserDetails(CUSTOMER)
	void customerCannotOpenAnotherCustomersAppointmentPages() throws Exception {
		mockMvc.perform(get("/customer/appointments/4/cancel"))
				.andExpect(status().isForbidden())
				.andExpect(content().string(containsString("Access denied")));
		mockMvc.perform(post("/customer/appointments/4/cancel").with(csrf()))
				.andExpect(status().isForbidden());
		mockMvc.perform(get("/customer/appointments/4/confirmation"))
				.andExpect(status().isForbidden());
		mockMvc.perform(get("/customer/book/9999"))
				.andExpect(status().isNotFound());
	}

	@Test
	@WithUserDetails(PROVIDER)
	void providerCannotOpenCustomerPages() throws Exception {
		mockMvc.perform(get("/customer/dashboard"))
				.andExpect(status().isForbidden())
				.andExpect(forwardedUrl("/access-denied"));
	}
}
