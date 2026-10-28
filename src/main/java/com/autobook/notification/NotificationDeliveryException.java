package com.autobook.notification;

/**
 * A notification could not be delivered. {@code retryable} is {@code false} when repeating the same
 * request cannot help (the service rejected it as invalid); timeouts, connection errors, and 5xx
 * responses are retryable.
 */
public class NotificationDeliveryException extends RuntimeException {

    private final boolean retryable;

    public NotificationDeliveryException(String message, boolean retryable, Throwable cause) {
        super(message, cause);
        this.retryable = retryable;
    }

    public boolean isRetryable() {
        return retryable;
    }
}
