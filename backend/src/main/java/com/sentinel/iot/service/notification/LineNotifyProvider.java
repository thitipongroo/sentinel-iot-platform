package com.sentinel.iot.service.notification;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * LINE Notify was shut down on March 31, 2025 — this provider is permanently disabled.
 * Use LineMessagingProvider instead (LINE_MESSAGING_ENABLED=true).
 */
@Component
@Slf4j
public class LineNotifyProvider implements NotificationProvider {

    @Override
    public void send(String message) {
        log.error("LineNotifyProvider is permanently disabled. LINE Notify was shut down March 31, 2025. " +
                  "Set LINE_MESSAGING_ENABLED=true and configure LINE_MESSAGING_CHANNEL_TOKEN + LINE_MESSAGING_TO.");
    }

    @Override
    public boolean isEnabled() {
        return false;
    }

    @Override
    public String providerName() {
        return "line-notify-tombstone";
    }
}
