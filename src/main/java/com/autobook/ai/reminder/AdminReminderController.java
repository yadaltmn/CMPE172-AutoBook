package com.autobook.ai.reminder;

import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Admin-only endpoints for the reminder agent (under /api/admin/**, so ADMIN role and CSRF apply).
 */
@RestController
public class AdminReminderController {

    private final ReminderAgentService reminderAgent;

    public AdminReminderController(ReminderAgentService reminderAgent) {
        this.reminderAgent = reminderAgent;
    }

    /** Runs the same reminder workflow the scheduler runs, immediately. */
    @PostMapping("/api/admin/ai/reminders/run")
    public ReminderRunResult run() {
        return reminderAgent.run(ReminderTrigger.MANUAL);
    }

    /** Recently queued reminders with their text, author (GEMINI or TEMPLATE), and delivery status. */
    @GetMapping("/api/admin/ai/reminders")
    public List<ReminderRecord> recent(@RequestParam(defaultValue = "50") int limit) {
        return reminderAgent.recent(limit);
    }
}
