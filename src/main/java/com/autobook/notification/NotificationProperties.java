package com.autobook.notification;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Settings for calls to the external notification service ({@code autobook.notifications.*}).
 *
 * @param baseUrl           base URL of the notification service; blank means the mock service in this application
 * @param timeout           connect and read timeout for one HTTP call
 * @param maxAttempts       HTTP attempts per delivery round (first try plus retries)
 * @param initialBackoff    wait before the first retry
 * @param backoffMultiplier growth factor of the wait between retries
 * @param maxBackoff        upper bound for the wait between retries
 * @param redeliveryDelay   fallback: how long a failed notification waits in the outbox before the next round
 * @param maxDeliveryRounds rounds before a notification is marked FAILED for manual follow-up
 * @param dispatchOnCommit  send right after the booking commits (the scheduled retry job runs either way)
 */
@ConfigurationProperties("autobook.notifications")
public record NotificationProperties(
        @DefaultValue("") String baseUrl,
        @DefaultValue("2s") Duration timeout,
        @DefaultValue("3") int maxAttempts,
        @DefaultValue("200ms") Duration initialBackoff,
        @DefaultValue("2.0") double backoffMultiplier,
        @DefaultValue("2s") Duration maxBackoff,
        @DefaultValue("30s") Duration redeliveryDelay,
        @DefaultValue("5") int maxDeliveryRounds,
        @DefaultValue("true") boolean dispatchOnCommit
) {
}
