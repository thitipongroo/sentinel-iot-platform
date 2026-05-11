package com.sentinel.iot.service;

import com.sentinel.iot.service.notification.NotificationProvider;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Fan-out to all enabled notification providers.
 * Add new providers by implementing {@link NotificationProvider} and registering as a Spring bean.
 */
@Service
@Slf4j
public class NotificationService {

    private final List<NotificationProvider> providers;

    public NotificationService(List<NotificationProvider> providers) {
        this.providers = providers;
    }

    public void send(String message) {
        boolean anySent = false;
        for (NotificationProvider provider : providers) {
            if (provider.isEnabled()) {
                provider.send(message);
                anySent = true;
            }
        }
        if (!anySent) {
            log.debug("No notification providers enabled. Message: {}", message);
        }
    }
}
