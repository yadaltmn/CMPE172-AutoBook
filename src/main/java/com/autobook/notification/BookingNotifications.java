package com.autobook.notification;

import com.autobook.dto.AppointmentDto;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Writes the customer notification for a booking or cancellation into the transactional outbox.
 *
 * <p>{@code Propagation.MANDATORY} enforces the first half of commit-then-notify: this can only run
 * inside the caller's booking or cancellation transaction. The outbox row commits or rolls back
 * together with the appointment, and nothing is sent to the external service here.
 */
@Component
public class BookingNotifications {

    private static final DateTimeFormatter WHEN =
            DateTimeFormatter.ofPattern("EEEE, MMMM d, yyyy 'at' h:mm a", Locale.US);

    private final NotificationOutboxRepository outboxRepository;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    public BookingNotifications(NotificationOutboxRepository outboxRepository, ApplicationEventPublisher events,
                                Clock clock) {
        this.outboxRepository = outboxRepository;
        this.events = events;
        this.clock = clock;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void bookingConfirmed(AppointmentDto appointment) {
        enqueue(appointment, NotificationType.BOOKING_CONFIRMED,
                "Your AutoBook appointment is confirmed",
                "Hi %s, your %s appointment with %s on %s is confirmed. Confirmation number: %s."
                        .formatted(appointment.customerName(), appointment.serviceName(), appointment.providerName(),
                                WHEN.format(appointment.startTime()), confirmationNumber(appointment)));
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void bookingCancelled(AppointmentDto appointment) {
        enqueue(appointment, NotificationType.BOOKING_CANCELLED,
                "Your AutoBook appointment was cancelled",
                "Hi %s, your %s appointment with %s on %s (confirmation number %s) has been cancelled."
                        .formatted(appointment.customerName(), appointment.serviceName(), appointment.providerName(),
                                WHEN.format(appointment.startTime()), confirmationNumber(appointment)));
    }

    public static String idempotencyKey(NotificationType type, long appointmentId) {
        return type.name() + "-appointment-" + appointmentId;
    }

    /** Same format as the confirmation page, for example AB-00005. */
    public static String confirmationNumber(AppointmentDto appointment) {
        return "AB-%05d".formatted(appointment.appointmentId());
    }

    private void enqueue(AppointmentDto appointment, NotificationType type, String subject, String message) {
        NotificationRequest request = new NotificationRequest(
                idempotencyKey(type, appointment.appointmentId()), type,
                appointment.customerEmail(), appointment.customerName(), subject, message,
                appointment.appointmentId(), confirmationNumber(appointment));
        outboxRepository.insertIfAbsent(request, LocalDateTime.now(clock))
                .ifPresent(outboxId -> events.publishEvent(new NotificationEnqueuedEvent(outboxId)));
    }
}
