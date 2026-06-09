package com.sentinel.iot.service.notification;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

/**
 * LINE Messaging API push-message provider.
 *
 * Required setup:
 *   1. Create a LINE Official Account and enable Messaging API at developers.line.biz
 *   2. Issue a Long-lived Channel Access Token
 *   3. Obtain the target userId (U...) or groupId (C...) — add bot as friend,
 *      send a test message, and capture the ID from the webhook event log
 *
 * Pricing (as of 2025): Free tier = 200 messages/month per Official Account.
 * Count is per recipient — broadcasting to a group of N users costs N messages.
 * See: https://developers.line.biz/en/docs/messaging-api/pricing/
 */
@Component
@Slf4j
public class LineMessagingProvider implements NotificationProvider {

    private static final String PUSH_URL = "https://api.line.me/v2/bot/message/push";

    @Value("${notification.line-messaging.channel-token:}")
    private String channelToken;

    @Value("${notification.line-messaging.to:}")
    private String to;

    @Value("${notification.line-messaging.enabled:false}")
    private boolean enabled;

    private final RestTemplate restTemplate = new RestTemplate();

    @Override
    @SuppressWarnings("null")
    public void send(String message) {
        if (!isEnabled()) return;
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(channelToken);

            String body = String.format(
                    "{\"to\":\"%s\",\"messages\":[{\"type\":\"text\",\"text\":\"%s\"}]}",
                    to, escapeJson(message));

            @SuppressWarnings("null")
            HttpEntity<String> entity = new HttpEntity<>(body, headers);
            ResponseEntity<String> resp = restTemplate.exchange(PUSH_URL, HttpMethod.POST, entity, String.class);
            log.info("LINE Messaging API sent. status={}", resp.getStatusCode());
        } catch (Exception e) {
            log.error("LINE Messaging API failed: {}", e.getMessage());
        }
    }

    @Override
    public boolean isEnabled() {
        return enabled
                && channelToken != null && !channelToken.isBlank()
                && to != null && !to.isBlank();
    }

    @Override
    public String providerName() {
        return "line-messaging";
    }

    private String escapeJson(String text) {
        return text.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n");
    }
}
