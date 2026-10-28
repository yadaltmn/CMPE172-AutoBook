package com.autobook.mocknotification;

import com.autobook.notification.NotificationReceipt;
import com.autobook.notification.NotificationRequest;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * HTTP API of the mock external notification service. It runs inside the AutoBook process for the
 * course demo, but AutoBook only reaches it over HTTP through {@code NotificationGateway}, exactly
 * as it would reach a real provider on another host.
 *
 * <ul>
 *     <li>{@code POST /mock-notifications/api/notifications}: 202 when delivered, 200 for a duplicate
 *     idempotency key, 503 during a simulated outage.</li>
 *     <li>{@code GET /mock-notifications/api/notifications}: delivered notifications (admin only).</li>
 *     <li>{@code GET/PUT /mock-notifications/api/faults}: view or change fault injection (admin only).</li>
 * </ul>
 */
@RestController
@RequestMapping("/mock-notifications/api")
public class MockNotificationController {

    private final MockNotificationService notificationService;
    private final MockNotificationFaults faults;

    public MockNotificationController(MockNotificationService notificationService, MockNotificationFaults faults) {
        this.notificationService = notificationService;
        this.faults = faults;
    }

    @PostMapping("/notifications")
    public ResponseEntity<?> send(@Valid @RequestBody NotificationRequest request) {
        try {
            NotificationReceipt receipt = notificationService.deliver(request);
            return ResponseEntity.status(receipt.duplicate() ? HttpStatus.OK : HttpStatus.ACCEPTED).body(receipt);
        } catch (MockNotificationUnavailableException ex) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(Map.of("status", 503, "error", "Service Unavailable", "message", ex.getMessage()));
        }
    }

    @GetMapping("/notifications")
    public List<MockNotificationService.DeliveredNotification> delivered() {
        return notificationService.delivered();
    }

    @GetMapping("/faults")
    public MockNotificationFaults.FaultSettings faults() {
        return faults.current();
    }

    @PutMapping("/faults")
    public MockNotificationFaults.FaultSettings updateFaults(@RequestBody MockNotificationFaults.FaultSettings settings) {
        faults.apply(settings);
        return faults.current();
    }
}
