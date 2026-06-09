package com.sentinel.iot.service.notification;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

/**
 * Apprise self-hosted notification provider.
 *
 * Apprise is an open-source notification gateway (130+ services) — run it as a sidecar
 * or dedicated service, then point this provider at it.
 *
 * Required setup:
 *   1. Deploy Apprise API: docker run -p 8000:8000 caronc/apprise
 *   2. Configure notification URLs inside Apprise (Telegram, Discord, email, LINE, etc.)
 *   3. Set APPRISE_URL=http://apprise:8000 and APPRISE_ENABLED=true
 *   4. Optionally set APPRISE_TAG to target a specific tag (omit to notify all configured services)
 *
 * API docs: https://github.com/caronc/apprise-api#api-details
 */
@Component
@Slf4j
public class AppriseNotificationProvider implements NotificationProvider {

    @Value("${notification.apprise.url:}")
    private String baseUrl;

    @Value("${notification.apprise.tag:}")
    private String tag;

    @Value("${notification.apprise.enabled:false}")
    private boolean enabled;

    private final RestTemplate restTemplate = new RestTemplate();

    @Override
    @SuppressWarnings("null")
    public void send(String message) {
        if (!isEnabled()) return;
        try {
            String endpoint = (tag != null && !tag.isBlank())
                    ? baseUrl.stripTrailing() + "/notify/" + tag
                    : baseUrl.stripTrailing() + "/notify";

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            String body = String.format("{\"title\":\"Sentinel Alert\",\"body\":\"%s\"}",
                    escapeJson(message));

            HttpEntity<String> entity = new HttpEntity<>(body, headers);
            ResponseEntity<String> resp = restTemplate.postForEntity(endpoint, entity, String.class);
            log.info("Apprise notification sent. status={}", resp.getStatusCode());
        } catch (Exception e) {
            log.error("Apprise notification failed: {}", e.getMessage());
        }
    }

    @Override
    public boolean isEnabled() {
        return enabled && baseUrl != null && !baseUrl.isBlank();
    }

    @Override
    public String providerName() {
        return "apprise";
    }

    private String escapeJson(String text) {
        return text.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n");
    }
}
