package com.autobook.ai.reminder;

import java.util.List;

/**
 * What one reminder run did.
 *
 * @param trigger             SCHEDULED or MANUAL
 * @param startedAt           run start (local time)
 * @param candidates          booked appointments in the window without a reminder
 * @param queuedByGemini      reminders written by Gemini and queued in the outbox
 * @param queuedFromTemplate  reminders using the template fallback and queued in the outbox
 * @param deferred            left for a later run because Gemini failed and the appointment is not close yet
 * @param skipped             already reminded meanwhile, or no longer booked
 * @param failed              unexpected errors (logged); the booking is unaffected
 * @param items               per-appointment outcome
 */
public record ReminderRunResult(
        ReminderTrigger trigger,
        String startedAt,
        int candidates,
        int queuedByGemini,
        int queuedFromTemplate,
        int deferred,
        int skipped,
        int failed,
        List<Item> items
) {

    public enum Outcome {
        QUEUED,
        DEFERRED,
        SKIPPED,
        FAILED
    }

    public record Item(long appointmentId, Outcome outcome, ReminderContentSource source, Long outboxId) {
    }
}
