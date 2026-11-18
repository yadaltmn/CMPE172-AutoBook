package com.autobook.ai.reminder;

import com.autobook.ai.AiModels;
import com.autobook.ai.AiProperties;
import com.autobook.dto.AppointmentDto;
import com.autobook.model.AppointmentStatus;
import com.autobook.notification.BookingNotifications;
import com.autobook.repository.AppointmentRepository;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.locks.ReentrantLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * The proactive reminder agent (Agent 2). Without any user request, it finds booked appointments
 * starting within the reminder window, asks Gemini to write a short personalized reminder, and
 * queues it through AutoBook's existing notification outbox. The outbox then delivers it after
 * commit with its usual timeout, retries, and fallback.
 *
 * <ul>
 *     <li><b>No duplicates:</b> the outbox key {@code APPOINTMENT_REMINDER-appointment-<id>} and the
 *     unique {@code appointment_reminders(appointment_id, reminder_type)} row are written in one
 *     transaction, so scheduled and manual runs, concurrent runs, and restarts cannot remind twice.</li>
 *     <li><b>Isolation:</b> the reminder transaction only inserts notification rows; it never changes an
 *     appointment or slot, and delivery happens after commit, so a notification failure cannot affect a booking.</li>
 *     <li><b>Gemini failure:</b> if Gemini is configured but fails, the appointment is left for a later run
 *     while it is more than {@code defer-if-ai-fails-until} away; closer to the appointment, the fixed template
 *     is sent and recorded as TEMPLATE, never as an AI-written reminder. Without a Gemini key the template is used.</li>
 * </ul>
 */
@Service
public class ReminderAgentService {

    private static final Logger log = LoggerFactory.getLogger(ReminderAgentService.class);

    private final AppointmentReminderRepository reminderRepository;
    private final AppointmentRepository appointmentRepository;
    private final BookingNotifications bookingNotifications;
    private final ReminderTextGenerator textGenerator;
    private final AiModels aiModels;
    private final AiProperties.Reminders settings;
    private final TransactionTemplate transactionTemplate;
    private final MeterRegistry meterRegistry;
    private final Clock clock;
    private final ReentrantLock running = new ReentrantLock();

    public ReminderAgentService(AppointmentReminderRepository reminderRepository,
                                AppointmentRepository appointmentRepository, BookingNotifications bookingNotifications,
                                ReminderTextGenerator textGenerator, AiModels aiModels, AiProperties properties,
                                TransactionTemplate transactionTemplate, MeterRegistry meterRegistry, Clock clock) {
        this.reminderRepository = reminderRepository;
        this.appointmentRepository = appointmentRepository;
        this.bookingNotifications = bookingNotifications;
        this.textGenerator = textGenerator;
        this.aiModels = aiModels;
        this.settings = properties.reminders();
        this.transactionTemplate = transactionTemplate;
        this.meterRegistry = meterRegistry;
        this.clock = clock;
    }

    public ReminderRunResult run(ReminderTrigger trigger) {
        // One run at a time in this process; the database constraint also covers other instances.
        running.lock();
        try {
            return runLocked(trigger);
        } finally {
            running.unlock();
        }
    }

    private ReminderRunResult runLocked(ReminderTrigger trigger) {
        LocalDateTime now = LocalDateTime.now(clock);
        List<Long> due = reminderRepository.findDueAppointmentIds(now, now.plus(settings.window()),
                AppointmentReminderRepository.UPCOMING_APPOINTMENT, Math.max(1, settings.batchSize()));
        List<ReminderRunResult.Item> items = new ArrayList<>();
        for (long appointmentId : due) {
            ReminderRunResult.Item item;
            try {
                item = remind(appointmentId, trigger, now);
            } catch (RuntimeException ex) {
                log.error("Reminder for appointment {} failed: {}", appointmentId, ex.toString());
                item = new ReminderRunResult.Item(appointmentId, ReminderRunResult.Outcome.FAILED, null, null);
            }
            items.add(item);
            meterRegistry.counter("autobook.ai.reminders", "outcome", outcomeTag(item)).increment();
        }
        ReminderRunResult result = new ReminderRunResult(trigger, now.toString(), due.size(),
                count(items, ReminderRunResult.Outcome.QUEUED, ReminderContentSource.GEMINI),
                count(items, ReminderRunResult.Outcome.QUEUED, ReminderContentSource.TEMPLATE),
                count(items, ReminderRunResult.Outcome.DEFERRED, null),
                count(items, ReminderRunResult.Outcome.SKIPPED, null),
                count(items, ReminderRunResult.Outcome.FAILED, null),
                List.copyOf(items));
        if (!due.isEmpty() || trigger == ReminderTrigger.MANUAL) {
            log.info("Reminder run ({}): {} due, {} written by Gemini, {} from template, {} deferred, {} skipped, {} failed",
                    trigger, result.candidates(), result.queuedByGemini(), result.queuedFromTemplate(), result.deferred(),
                    result.skipped(), result.failed());
        }
        return result;
    }

    private ReminderRunResult.Item remind(long appointmentId, ReminderTrigger trigger, LocalDateTime now) {
        Optional<AppointmentDto> found = appointmentRepository.findById(appointmentId);
        if (found.isEmpty() || found.get().status() != AppointmentStatus.BOOKED
                || reminderRepository.exists(appointmentId, AppointmentReminderRepository.UPCOMING_APPOINTMENT)) {
            return new ReminderRunResult.Item(appointmentId, ReminderRunResult.Outcome.SKIPPED, null, null);
        }
        AppointmentDto appointment = found.get();

        // The Gemini call happens outside any transaction: no database lock is held while waiting for it.
        ReminderTextGenerator.GeneratedReminder reminder = textGenerator.generateWithAi(appointment).orElse(null);
        if (reminder == null) {
            if (aiModels.chatAvailable() && appointment.startTime().isAfter(now.plus(settings.deferIfAiFailsUntil()))) {
                log.warn("Reminder for appointment {} deferred: Gemini unavailable, will retry on a later run", appointmentId);
                return new ReminderRunResult.Item(appointmentId, ReminderRunResult.Outcome.DEFERRED, null, null);
            }
            reminder = textGenerator.template(appointment);
            if (aiModels.chatAvailable()) {
                log.warn("Reminder for appointment {} uses the template: Gemini unavailable close to the appointment",
                        appointmentId);
            }
        }

        ReminderTextGenerator.GeneratedReminder text = reminder;
        try {
            Long outboxId = transactionTemplate.execute(status -> {
                AppointmentDto current = appointmentRepository.findById(appointmentId).orElse(null);
                if (current == null || current.status() != AppointmentStatus.BOOKED) {
                    return null;
                }
                Optional<Long> queued = bookingNotifications.appointmentReminder(current, text.text());
                if (queued.isEmpty()) {
                    return null;
                }
                reminderRepository.insert(appointmentId, AppointmentReminderRepository.UPCOMING_APPOINTMENT,
                        queued.get(), text.source(), text.model(), trigger, now);
                return queued.get();
            });
            if (outboxId == null) {
                return new ReminderRunResult.Item(appointmentId, ReminderRunResult.Outcome.SKIPPED, null, null);
            }
            log.info("Reminder for appointment {} queued (outbox {}, {})", appointmentId, outboxId, text.source());
            return new ReminderRunResult.Item(appointmentId, ReminderRunResult.Outcome.QUEUED, text.source(), outboxId);
        } catch (DuplicateKeyException ex) {
            // Another run queued it first; the whole transaction was rolled back.
            return new ReminderRunResult.Item(appointmentId, ReminderRunResult.Outcome.SKIPPED, null, null);
        }
    }

    public List<ReminderRecord> recent(int limit) {
        return reminderRepository.findRecent(Math.min(Math.max(limit, 1), 200));
    }

    private static int count(List<ReminderRunResult.Item> items, ReminderRunResult.Outcome outcome,
                             ReminderContentSource source) {
        return (int) items.stream()
                .filter(item -> item.outcome() == outcome && (source == null || item.source() == source))
                .count();
    }

    private static String outcomeTag(ReminderRunResult.Item item) {
        if (item.outcome() == ReminderRunResult.Outcome.QUEUED) {
            return item.source() == ReminderContentSource.GEMINI ? "queued_gemini" : "queued_template";
        }
        return item.outcome().name().toLowerCase();
    }
}
