package com.autobook.mocknotification;

/**
 * Simulated outage of the external notification service (answered with HTTP 503).
 */
public class MockNotificationUnavailableException extends RuntimeException {

    public MockNotificationUnavailableException(String message) {
        super(message);
    }
}
