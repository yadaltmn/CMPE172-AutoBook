package com.autobook.notification;

import com.autobook.observability.NotificationMetrics;
import jakarta.annotation.PreDestroy;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicInteger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Delivers outbox notifications: commit-then-notify, retries, and fallback.
 *
 * <ol>
 *     <li><b>Commit, then notify.</b> {@link #onNotificationEnqueued} runs only in the
 *     {@code AFTER_COMMIT} phase, and hands the work to a background thread. The customer's booking
 *     request never waits for, and can never be rolled back by, the notification service.</li>
 *     <li><b>Retry.</b> One delivery round uses {@link ResilientNotificationSender}: per-call timeout,
 *     bounded attempts, exponential backoff.</li>
 *     <li><b>Fallback.</b> If a round fails, the row stays PENDING in the outbox with a later
 *     {@code next_attempt_at}, and {@link #retryDueNotifications} delivers it once the service is back,
 *     including after an application restart. After {@code maxDeliveryRounds} it is marked FAILED and
 *     logged as an error for manual follow-up. The appointment is never changed.</li>
 * </ol>
 */
@Component
public class NotificationDispatcher {

    private static final Logger log = LoggerFactory.getLogger(NotificationDispatcher.class);
    private static final Duration MIN_LEASE = Duration.ofMinutes(1);
    private static final int RETRY_BATCH_SIZE = 25;

    private final NotificationOutboxRepository outboxRepository;
    private final ResilientNotificationSender sender;
    private final NotificationProperties properties;
    private final NotificationMetrics metrics;
    private final Clock clock;
    private final ExecutorService executor;

    public NotificationDispatcher(NotificationOutboxRepository outboxRepository, ResilientNotificationSender sender,
                                  NotificationProperties properties, NotificationMetrics metrics, Clock clock) {
        this.outboxRepository = outboxRepository;
        this.sender = sender;
        this.properties = properties;
        this.metrics = metrics;
        this.clock = clock;
        AtomicInteger threadNumber = new AtomicInteger();
        this.executor = Executors.newFixedThreadPool(2, runnable -> {
            Thread thread = new Thread(runnable, "notification-dispatch-" + threadNumber.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        });
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onNotificationEnqueued(NotificationEnqueuedEvent event) {
        if (!properties.dispatchOnCommit()) {
            return;
        }
        try {
            executor.execute(() -> deliver(event.outboxId()));
        } catch (RejectedExecutionException ex) {
            // Shutting down: the row is still PENDING and the retry job sends it after restart.
            log.warn("Notification outbox {} not dispatched now ({}); it stays queued for retry",
                    event.outboxId(), ex.getClass().getSimpleName());
        }
    }

    /**
     * Retry job: delivers every PENDING notification whose next attempt is due.
     */
    @Scheduled(fixedDelayString = "${autobook.notifications.retry-interval-ms:15000}",
            initialDelayString = "${autobook.notifications.retry-initial-delay-ms:10000}")
    public void retryDueNotifications() {
        List<Long> due;
        try {
            due = outboxRepository.findDueIds(LocalDateTime.now(clock), RETRY_BATCH_SIZE);
        } catch (RuntimeException ex) {
            log.error("Notification retry job could not read the outbox: {}", ex.getMessage());
            return;
        }
        if (!due.isEmpty()) {
            log.info("Notification retry job found {} due notification(s)", due.size());
        }
        due.forEach(this::deliver);
    }

    /**
     * Runs one delivery round for one outbox row, if it is due and no other thread owns it.
     */
    public DeliveryOutcome deliver(long outboxId) {
        try {
            LocalDateTime now = LocalDateTime.now(clock);
            if (!outboxRepository.claim(outboxId, now, now.plus(lease()))) {
                return DeliveryOutcome.SKIPPED;
            }
            OutboxEntry entry = outboxRepository.findById(outboxId).orElseThrow();
            return attempt(entry);
        } catch (RuntimeException ex) {
            // For example the database is down. The lease expires and the retry job tries again.
            log.error("Notification outbox {} could not be processed: {}", outboxId, ex.toString());
            return DeliveryOutcome.ERROR;
        }
    }

    private DeliveryOutcome attempt(OutboxEntry entry) {
        try {
            ResilientNotificationSender.DeliveryResult result = sender.send(entry.toRequest());
            outboxRepository.markSent(entry.outboxId(), result.attempts(), result.receipt().notificationId(),
                    LocalDateTime.now(clock));
            log.info("Notification {} for appointment {} sent (outbox {}, attempts {}, duplicate={})",
                    entry.type(), entry.appointmentId(), entry.outboxId(), result.attempts(), result.receipt().duplicate());
            metrics.roundFinished("sent");
            return DeliveryOutcome.SENT;
        } catch (ResilientNotificationSender.DeliveryFailedException ex) {
            return fallback(entry, ex);
        }
    }

    private DeliveryOutcome fallback(OutboxEntry entry, ResilientNotificationSender.DeliveryFailedException ex) {
        int round = entry.deliveryRounds() + 1;
        if (!ex.isRetryable() || round >= properties.maxDeliveryRounds()) {
            outboxRepository.markFailed(entry.outboxId(), ex.attempts(), ex.getMessage());
            log.error("Notification {} for appointment {} FAILED after {} round(s): {} The appointment is kept;"
                            + " follow up manually (outbox {}).",
                    entry.type(), entry.appointmentId(), round, ex.getMessage(), entry.outboxId());
            metrics.roundFinished("failed");
            return DeliveryOutcome.FAILED;
        }
        Duration wait = properties.redeliveryDelay().multipliedBy(round);
        outboxRepository.scheduleRetry(entry.outboxId(), ex.attempts(), ex.getMessage(),
                LocalDateTime.now(clock).plus(wait));
        log.warn("Notification {} for appointment {} not delivered (round {}/{}): {} Kept in outbox {}, next try in {} s",
                entry.type(), entry.appointmentId(), round, properties.maxDeliveryRounds(), ex.getMessage(),
                entry.outboxId(), wait.toSeconds());
        metrics.roundFinished("retry_scheduled");
        return DeliveryOutcome.RETRY_SCHEDULED;
    }

    /**
     * Upper bound for one round (all attempts plus backoff), so a crashed round is retried later.
     */
    private Duration lease() {
        int attempts = Math.max(1, properties.maxAttempts());
        Duration worstCase = properties.timeout().multipliedBy(2L * attempts)
                .plus(properties.maxBackoff().multipliedBy(attempts));
        return worstCase.compareTo(MIN_LEASE) > 0 ? worstCase : MIN_LEASE;
    }

    @PreDestroy
    void shutdown() {
        executor.shutdown();
    }

    public enum DeliveryOutcome {
        SENT,
        RETRY_SCHEDULED,
        FAILED,
        SKIPPED,
        ERROR
    }
}
