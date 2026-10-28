package com.autobook.observability;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

/**
 * Booking metrics, readable at {@code GET /metrics/<name>} (admin only).
 *
 * <ul>
 *     <li>{@code autobook.bookings.created}: successful bookings</li>
 *     <li>{@code autobook.bookings.conflicts}: booking attempts rejected because the slot was taken (409)</li>
 *     <li>{@code autobook.bookings.cancelled}: cancellations</li>
 *     <li>{@code autobook.booking.duration}: time spent in the booking transaction, tagged by outcome</li>
 * </ul>
 */
@Component
public class BookingMetrics {

    private final MeterRegistry registry;
    private final Counter created;
    private final Counter conflicts;
    private final Counter cancelled;

    public BookingMetrics(MeterRegistry registry) {
        this.registry = registry;
        this.created = Counter.builder("autobook.bookings.created")
                .description("Appointments booked successfully").register(registry);
        this.conflicts = Counter.builder("autobook.bookings.conflicts")
                .description("Booking attempts rejected because the slot was already taken").register(registry);
        this.cancelled = Counter.builder("autobook.bookings.cancelled")
                .description("Appointments cancelled by customers").register(registry);
    }

    public Timer.Sample startBooking() {
        return Timer.start(registry);
    }

    public void bookingSucceeded(Timer.Sample sample) {
        created.increment();
        sample.stop(durationTimer("success"));
    }

    public void bookingConflicted(Timer.Sample sample) {
        conflicts.increment();
        sample.stop(durationTimer("conflict"));
    }

    public void bookingRejected(Timer.Sample sample) {
        sample.stop(durationTimer("rejected"));
    }

    public void appointmentCancelled() {
        cancelled.increment();
    }

    private Timer durationTimer(String outcome) {
        return Timer.builder("autobook.booking.duration")
                .description("Time spent in the booking transaction")
                .tag("outcome", outcome)
                .register(registry);
    }
}
