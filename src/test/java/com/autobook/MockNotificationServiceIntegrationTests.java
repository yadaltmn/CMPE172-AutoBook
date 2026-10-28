package com.autobook;

import com.autobook.mocknotification.MockNotificationFaults;
import com.autobook.mocknotification.MockNotificationService;
import com.autobook.notification.NotificationRequest;
import com.autobook.notification.NotificationType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithUserDetails;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Contract of the mock external notification service: delivery, idempotency, simulated outage,
 * request validation, and admin-only inspection.
 */
@SpringBootTest
@AutoConfigureMockMvc
class MockNotificationServiceIntegrationTests {

	private static final String ADMIN = "morgan.lee@example.com";
	private static final String CUSTOMER = "jada.nguyen@example.com";

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private MockNotificationService notificationService;

	@Autowired
	private MockNotificationFaults faults;

	@BeforeEach
	@AfterEach
	void resetMockService() {
		notificationService.clear();
		faults.reset();
	}

	@Test
	void deliversNotificationWithoutBrowserSessionOrCsrfToken() throws Exception {
		mockMvc.perform(post("/mock-notifications/api/notifications")
						.contentType(MediaType.APPLICATION_JSON)
						.content(request("test-key-1")))
				.andExpect(status().isAccepted())
				.andExpect(jsonPath("$.idempotencyKey", is("test-key-1")))
				.andExpect(jsonPath("$.duplicate", is(false)));

		assertTrue(notificationService.hasDelivered("test-key-1"));
	}

	@Test
	void repeatedIdempotencyKeyIsAcknowledgedButNotDeliveredTwice() throws Exception {
		mockMvc.perform(post("/mock-notifications/api/notifications")
						.contentType(MediaType.APPLICATION_JSON).content(request("test-key-2")))
				.andExpect(status().isAccepted());

		mockMvc.perform(post("/mock-notifications/api/notifications")
						.contentType(MediaType.APPLICATION_JSON).content(request("test-key-2")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.duplicate", is(true)));

		assertEquals(1, notificationService.delivered().size());
		assertEquals(2, notificationService.receivedRequests());
	}

	@Test
	void simulatedOutageReturns503AndDeliversNothing() throws Exception {
		faults.apply(new MockNotificationFaults.FaultSettings(true, 0, 0));

		mockMvc.perform(post("/mock-notifications/api/notifications")
						.contentType(MediaType.APPLICATION_JSON).content(request("test-key-3")))
				.andExpect(status().isServiceUnavailable());

		assertEquals(0, notificationService.delivered().size());
	}

	@Test
	void failNextRequestsFailsThenRecovers() throws Exception {
		faults.apply(new MockNotificationFaults.FaultSettings(false, 1, 0));

		mockMvc.perform(post("/mock-notifications/api/notifications")
						.contentType(MediaType.APPLICATION_JSON).content(request("test-key-4")))
				.andExpect(status().isServiceUnavailable());
		mockMvc.perform(post("/mock-notifications/api/notifications")
						.contentType(MediaType.APPLICATION_JSON).content(request("test-key-4")))
				.andExpect(status().isAccepted());
	}

	@Test
	void incompleteRequestIsRejected() throws Exception {
		mockMvc.perform(post("/mock-notifications/api/notifications")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"idempotencyKey\":\"test-key-5\",\"type\":\"BOOKING_CONFIRMED\"}"))
				.andExpect(status().isBadRequest());

		assertEquals(0, notificationService.delivered().size());
	}

	@Test
	void deliveredListAndFaultSwitchesAreAdminOnly() throws Exception {
		mockMvc.perform(get("/mock-notifications/api/notifications"))
				.andExpect(status().isUnauthorized());
	}

	@Test
	@WithUserDetails(CUSTOMER)
	void customersCannotInspectDeliveries() throws Exception {
		mockMvc.perform(get("/mock-notifications/api/notifications"))
				.andExpect(status().isForbidden());
	}

	@Test
	@WithUserDetails(ADMIN)
	void adminCanInspectDeliveriesAndInjectFaults() throws Exception {
		notificationService.deliver(new NotificationRequest(
				"test-key-6", NotificationType.BOOKING_CONFIRMED,
				"customer@example.com", "Test Customer", "Subject", "Message", 1L, "AB-00001"));

		mockMvc.perform(get("/mock-notifications/api/notifications"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$", hasSize(1)));

		mockMvc.perform(put("/mock-notifications/api/faults").with(csrf())
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"unavailable\":false,\"failNextRequests\":2,\"delayMillis\":0}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.failNextRequests", is(2)));
	}

	private static String request(String key) {
		return """
				{
				  "idempotencyKey": "%s",
				  "type": "BOOKING_CONFIRMED",
				  "recipientEmail": "customer@example.com",
				  "recipientName": "Test Customer",
				  "subject": "Your AutoBook appointment is confirmed",
				  "message": "Your Oil Change appointment is confirmed.",
				  "appointmentId": 1,
				  "confirmationNumber": "AB-00001"
				}
				""".formatted(key);
	}
}
