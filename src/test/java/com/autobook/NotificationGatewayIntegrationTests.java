package com.autobook;

import com.autobook.mocknotification.MockNotificationFaults;
import com.autobook.mocknotification.MockNotificationService;
import com.autobook.notification.NotificationDeliveryException;
import com.autobook.notification.NotificationGateway;
import com.autobook.notification.NotificationReceipt;
import com.autobook.notification.NotificationRequest;
import com.autobook.notification.NotificationType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Calls the mock notification service over real HTTP through {@link NotificationGateway}:
 * success, idempotent repeat, outage, and timeout.
 */
@SpringBootTest(
		webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
		properties = "autobook.notifications.timeout=300ms")
class NotificationGatewayIntegrationTests {

	@Autowired
	private NotificationGateway gateway;

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
	void sendsOneCoarseGrainedRequestOverHttp() {
		NotificationReceipt receipt = gateway.send(request("gateway-key-1"));

		assertFalse(receipt.duplicate());
		assertEquals("gateway-key-1", receipt.idempotencyKey());
		assertTrue(notificationService.hasDelivered("gateway-key-1"));
	}

	@Test
	void repeatingAKeyReturnsTheOriginalReceiptWithoutDeliveringAgain() {
		NotificationReceipt first = gateway.send(request("gateway-key-2"));
		NotificationReceipt second = gateway.send(request("gateway-key-2"));

		assertTrue(second.duplicate());
		assertEquals(first.notificationId(), second.notificationId());
		assertEquals(1, notificationService.delivered().size());
	}

	@Test
	void outageIsReportedAsRetryableFailure() {
		faults.apply(new MockNotificationFaults.FaultSettings(true, 0, 0));

		NotificationDeliveryException ex = assertThrows(NotificationDeliveryException.class,
				() -> gateway.send(request("gateway-key-3")));

		assertTrue(ex.isRetryable());
		assertTrue(ex.getMessage().contains("503"), ex.getMessage());
	}

	@Test
	void slowServiceTimesOutWithinTheConfiguredLimit() throws InterruptedException {
		faults.apply(new MockNotificationFaults.FaultSettings(false, 0, 1500));

		long started = System.nanoTime();
		NotificationDeliveryException ex = assertThrows(NotificationDeliveryException.class,
				() -> gateway.send(request("gateway-key-4")));
		long elapsedMillis = (System.nanoTime() - started) / 1_000_000;

		assertTrue(ex.isRetryable());
		assertTrue(ex.getMessage().contains("did not answer within 300 ms"), ex.getMessage());
		assertTrue(elapsedMillis < 1400, "gateway gave up after " + elapsedMillis + " ms");

		// The slow server still finishes after the client gave up; this is the lost-response case
		// that idempotency keys exist for. Wait for it so it cannot leak into the next test.
		awaitDelivery("gateway-key-4");
	}

	private void awaitDelivery(String key) throws InterruptedException {
		long deadline = System.currentTimeMillis() + 5000;
		while (!notificationService.hasDelivered(key) && System.currentTimeMillis() < deadline) {
			Thread.sleep(50);
		}
		assertTrue(notificationService.hasDelivered(key), "server finished the slow request");
	}

	private static NotificationRequest request(String key) {
		return new NotificationRequest(key, NotificationType.BOOKING_CONFIRMED, "customer@example.com",
				"Test Customer", "Your AutoBook appointment is confirmed", "Message body", 1L, "AB-00001");
	}
}
