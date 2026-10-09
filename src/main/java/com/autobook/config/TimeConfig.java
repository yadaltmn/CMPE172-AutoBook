package com.autobook.config;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class TimeConfig {

    /**
     * Single source of "now" for booking rules, so the future-slot checks can be reasoned about in tests.
     */
    @Bean
    public Clock clock() {
        return Clock.systemDefaultZone();
    }
}
