package com.sentinel.iot.service.notification;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

/**
 * LINE Notify integration — deprecated by LINE Corp.
 * Migrate to LINE Messaging API or use SlackNotificationProvider / WebhookNotificationProvider.
 */
@Component
@Slf4j
public class LineNotifyProvider implements NotificationProvider {

    private static final String LINE_NOTIFY_URL = "https://notify-api.line.me/api/notify";

    @Value("${notification.line.token:}")
    private String token;

    @Value("${notification.line.enabled:false}")
    private boolean enabled;

    private final RestTemplate restTemplate = new RestTemplate();

    @Override
    public void send(String message) {
        if (!isEnabled()) return;
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
            headers.setBearerAuth(token);
            HttpEntity<String> entity = new HttpEntity<>("message=" + message, headers);
            ResponseEntity<String> resp = restTemplate.exchange(LINE_NOTIFY_URL, HttpMethod.POST, entity, String.class);
            log.info("LINE Notify sent. status={}", resp.getStatusCode());
        } catch (Exception e) {
            log.error("LINE Notify failed: {}", e.getMessage());
        }
    }

    @Override
    public boolean isEnabled() {
        return enabled && token != null && !token.isBlank();
    }

    @Override
    public String providerName() {
        return "line-notify";
    }
}
