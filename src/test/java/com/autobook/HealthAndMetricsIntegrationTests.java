package com.autobook;

import com.autobook.dto.BookingRequest;
import com.autobook.exception.BookingConflictException;
import com.autobook.model.AppUser;
import com.autobook.repository.UserRepository;
import com.autobook.service.BookingService;
import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.test.context.support.WithUserDetails;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * GET /health with its database check, and the booking metrics.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class HealthAndMetricsIntegrationTests {

	private static final String ADMIN = "morgan.lee@example.com";
	private static final String CUSTOMER = "jada.nguyen@example.com";

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private MeterRegistry meterRegistry;

	@Autowired
	private BookingService bookingService;

	@Autowired
	private UserRepository userRepository;

	@Test
	void healthIsPublicAndReportsDatabaseConnectivity() throws Exception {
		mockMvc.perform(get("/health"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status", is("UP")))
				.andExpect(jsonPath("$.components.db.status", is("UP")))
				.andExpect(jsonPath("$.components.notificationOutbox.status", is("UP")))
				// Anonymous callers see component status only, not database details.
				.andExpect(jsonPath("$.components.db.details").doesNotExist());
	}

	@Test
	void livenessAndReadinessProbesArePublic() throws Exception {
		mockMvc.perform(get("/health/liveness")).andExpect(status().isOk());
		mockMvc.perform(get("/health/readiness")).andExpect(status().isOk());
	}

	@Test
	@WithUserDetails(ADMIN)
	void adminSeesDatabaseDetails() throws Exception {
		mockMvc.perform(get("/health"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.components.db.details.database", is("PostgreSQL")))
				.andExpect(jsonPath("$.components.notificationOutbox.details.failed").exists());
	}

	@Test
	void metricsRequireAuthentication() throws Exception {
		mockMvc.perform(get("/metrics/autobook.bookings.created"))
				.andExpect(status().isUnauthorized());
	}

	@Test
	@WithUserDetails(CUSTOMER)
	void customersCannotReadMetrics() throws Exception {
		mockMvc.perform(get("/metrics/autobook.bookings.created"))
				.andExpect(status().isForbidden());
	}

	@Test
	@WithUserDetails(ADMIN)
	void bookingAndConflictCountersAreRecorded() throws Exception {
		AppUser jada = userRepository.findByEmail(CUSTOMER).orElseThrow();
		double createdBefore = meterRegistry.counter("autobook.bookings.created").count();
		double conflictsBefore = meterRegistry.counter("autobook.bookings.conflicts").count();

		bookingService.book(jada, new BookingRequest(2L, null));
		// Seed slot 6 is already booked.
		assertThrows(BookingConflictException.class, () -> bookingService.book(jada, new BookingRequest(6L, null)));

		assertEquals(createdBefore + 1, meterRegistry.counter("autobook.bookings.created").count());
		assertEquals(conflictsBefore + 1, meterRegistry.counter("autobook.bookings.conflicts").count());

		mockMvc.perform(get("/metrics/autobook.bookings.created"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.name", is("autobook.bookings.created")))
				.andExpect(jsonPath("$.measurements[0].statistic", is("COUNT")));
		mockMvc.perform(get("/metrics/autobook.booking.duration").param("tag", "outcome:success"))
				.andExpect(status().isOk());
	}
}
