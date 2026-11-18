package com.autobook.ai.reminder;

import com.autobook.ai.AiProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Runs the reminder agent on a schedule, with no user session: every 15 minutes by default
 * ({@code autobook.ai.reminders.interval-ms}), first one minute after startup.
 */
@Component
public class ReminderScheduler {

    private static final Logger log = LoggerFactory.getLogger(ReminderScheduler.class);

    private final ReminderAgentService reminderAgent;
    private final AiProperties properties;

    public ReminderScheduler(ReminderAgentService reminderAgent, AiProperties properties) {
        this.reminderAgent = reminderAgent;
        this.properties = properties;
    }

    @Scheduled(fixedDelayString = "${autobook.ai.reminders.interval-ms:900000}",
            initialDelayString = "${autobook.ai.reminders.initial-delay-ms:60000}")
    public void runScheduled() {
        if (!properties.reminders().enabled()) {
            return;
        }
        try {
            reminderAgent.run(ReminderTrigger.SCHEDULED);
        } catch (RuntimeException ex) {
            log.error("Scheduled reminder run failed: {}", ex.toString());
        }
    }
}
