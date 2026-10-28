package com.autobook.notification;

public enum OutboxStatus {
    /** Waiting for its first or next delivery round. */
    PENDING,
    /** Delivered (or acknowledged as a duplicate) by the notification service. */
    SENT,
    /** Gave up after the configured rounds; needs manual follow-up. The booking itself is unaffected. */
    FAILED
}
