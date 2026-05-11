package com.sentinel.iot.service.notification;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;

/**
 * Generic outbound webhook — sends a JSON POST to any HTTP endpoint.
 * If NOTIFY_WEBHOOK_SECRET is set, signs the payload with HMAC-SHA256
 * and adds an X-Sentinel-Signature header for the receiving end to verify.
 */
@Component
@Slf4j
public class WebhookNotificationProvider implements NotificationProvider {

    @Value("${notification.webhook.url:}")
    private String webhookUrl;

    @Value("${notification.webhook.enabled:false}")
    private boolean enabled;

    @Value("${notification.webhook.secret:}")
    private String secret;

    private final RestTemplate restTemplate = new RestTemplate();

    @Override
    public void send(String message) {
        if (!isEnabled()) return;
        try {
            String timestamp = String.valueOf(Instant.now().getEpochSecond());
            String body = "{\"message\": \"" + escapeJson(message) + "\", \"timestamp\": \"" + timestamp + "\"}";

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            if (secret != null && !secret.isBlank()) {
                headers.set("X-Sentinel-Signature", "sha256=" + sign(body));
                headers.set("X-Sentinel-Timestamp", timestamp);
            }

            HttpEntity<String> entity = new HttpEntity<>(body, headers);
            ResponseEntity<String> resp = restTemplate.postForEntity(webhookUrl, entity, String.class);
            log.info("Webhook notification sent. status={}", resp.getStatusCode());
        } catch (Exception e) {
            log.error("Webhook notification failed: {}", e.getMessage());
        }
    }

    @Override
    public boolean isEnabled() {
        return enabled && webhookUrl != null && !webhookUrl.isBlank();
    }

    @Override
    public String providerName() {
        return "webhook";
    }

    private String sign(String payload) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return Base64.getEncoder().encodeToString(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
    }

    private String escapeJson(String text) {
        return text.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n");
    }
}
