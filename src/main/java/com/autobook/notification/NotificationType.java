package com.autobook.notification;

/**
 * Business events that AutoBook announces to customers through the external notification service.
 */
public enum NotificationType {
    BOOKING_CONFIRMED,
    BOOKING_CANCELLED,
    /** Milestone 4: reminder before an upcoming appointment, written by the reminder agent. */
    APPOINTMENT_REMINDER
}
