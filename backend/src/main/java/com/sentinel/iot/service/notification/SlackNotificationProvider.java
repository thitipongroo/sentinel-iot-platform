package com.sentinel.iot.service.notification;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

@Component
@Slf4j
public class SlackNotificationProvider implements NotificationProvider {

    @Value("${notification.slack.webhook-url:}")
    private String webhookUrl;

    @Value("${notification.slack.enabled:false}")
    private boolean enabled;

    private final RestTemplate restTemplate = new RestTemplate();

    @Override
    public void send(String message) {
        if (!isEnabled()) return;
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            String body = "{\"text\": \"" + escapeJson(message) + "\"}";
            HttpEntity<String> entity = new HttpEntity<>(body, headers);
            ResponseEntity<String> resp = restTemplate.postForEntity(webhookUrl, entity, String.class);
            log.info("Slack notification sent. status={}", resp.getStatusCode());
        } catch (Exception e) {
            log.error("Slack notification failed: {}", e.getMessage());
        }
    }

    @Override
    public boolean isEnabled() {
        return enabled && webhookUrl != null && !webhookUrl.isBlank();
    }

    @Override
    public String providerName() {
        return "slack";
    }

    private String escapeJson(String text) {
        return text.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n");
    }
}
