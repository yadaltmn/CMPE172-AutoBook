package com.autobook.controller;

import com.autobook.notification.NotificationOutboxRepository;
import com.autobook.notification.OutboxEntry;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Lets admins see the notification outbox: what was sent, what is waiting for a retry, and what failed.
 */
@RestController
@RequestMapping("/api/admin/notifications")
public class AdminNotificationApiController {

    private final NotificationOutboxRepository outboxRepository;

    public AdminNotificationApiController(NotificationOutboxRepository outboxRepository) {
        this.outboxRepository = outboxRepository;
    }

    @GetMapping
    public List<OutboxEntry> recentNotifications() {
        return outboxRepository.findRecent(100);
    }
}
