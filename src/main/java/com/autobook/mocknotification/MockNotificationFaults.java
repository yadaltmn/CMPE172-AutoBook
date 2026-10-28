package com.autobook.mocknotification;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Fault switches for the mock notification service, used to demonstrate and test how AutoBook
 * behaves when the external service is down, failing, or slow.
 */
@Component
public class MockNotificationFaults {

    private final AtomicBoolean unavailable = new AtomicBoolean();
    private final AtomicInteger failNextRequests = new AtomicInteger();
    private final AtomicLong delayMillis = new AtomicLong();

    public MockNotificationFaults(
            @Value("${autobook.mock-notifications.unavailable:false}") boolean unavailable,
            @Value("${autobook.mock-notifications.delay-ms:0}") long delayMillis) {
        this.unavailable.set(unavailable);
        this.delayMillis.set(Math.max(0, delayMillis));
    }

    public FaultSettings current() {
        return new FaultSettings(unavailable.get(), failNextRequests.get(), delayMillis.get());
    }

    public void apply(FaultSettings settings) {
        unavailable.set(settings.unavailable());
        failNextRequests.set(Math.max(0, settings.failNextRequests()));
        delayMillis.set(Math.max(0, settings.delayMillis()));
    }

    public void reset() {
        apply(new FaultSettings(false, 0, 0));
    }

    boolean isUnavailable() {
        return unavailable.get();
    }

    /**
     * Returns {@code true} (and uses up one failure) if this request should fail with 503.
     */
    boolean consumeFailure() {
        return failNextRequests.getAndUpdate(remaining -> remaining > 0 ? remaining - 1 : 0) > 0;
    }

    long delayMillis() {
        return delayMillis.get();
    }

    /**
     * @param unavailable      every request fails with 503 while this is {@code true}
     * @param failNextRequests the next N requests fail with 503, then the service recovers
     * @param delayMillis      artificial processing delay, used to trigger client timeouts
     */
    public record FaultSettings(boolean unavailable, int failNextRequests, long delayMillis) {
    }
}
