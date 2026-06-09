package com.sentinel.iot.service.notification;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

/**
 * Telegram Bot API notification provider.
 *
 * Required setup:
 *   1. Create a bot via @BotFather on Telegram — copy the token
 *   2. Add the bot to the target group (or start a chat with it directly)
 *   3. Obtain the chat_id:
 *      - For a user: send any message to the bot, then call
 *        https://api.telegram.org/bot{TOKEN}/getUpdates and read result[0].message.chat.id
 *      - For a group: add bot to group, send a message, call getUpdates — read result[0].message.chat.id
 *        (group IDs are negative numbers, e.g. -1001234567890)
 *
 * Rate limit: 30 messages/second (free tier). No message quota per month.
 * See: https://core.telegram.org/bots/faq#how-can-i-message-all-of-my-bot-39s-subscribers-at-once
 */
@Component
@Slf4j
public class TelegramNotificationProvider implements NotificationProvider {

    private static final String API_BASE = "https://api.telegram.org/bot";

    @Value("${notification.telegram.bot-token:}")
    private String botToken;

    @Value("${notification.telegram.chat-id:}")
    private String chatId;

    @Value("${notification.telegram.enabled:false}")
    private boolean enabled;

    private final RestTemplate restTemplate = new RestTemplate();

    @Override
    @SuppressWarnings("null")
    public void send(String message) {
        if (!isEnabled()) return;
        try {
            String url = API_BASE + botToken + "/sendMessage";
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            String body = String.format("{\"chat_id\":\"%s\",\"text\":\"%s\"}",
                    chatId, escapeJson(message));

            HttpEntity<String> entity = new HttpEntity<>(body, headers);
            ResponseEntity<String> resp = restTemplate.postForEntity(url, entity, String.class);
            log.info("Telegram notification sent. status={}", resp.getStatusCode());
        } catch (Exception e) {
            log.error("Telegram notification failed: {}", e.getMessage());
        }
    }

    @Override
    public boolean isEnabled() {
        return enabled
                && botToken != null && !botToken.isBlank()
                && chatId != null && !chatId.isBlank();
    }

    @Override
    public String providerName() {
        return "telegram";
    }

    private String escapeJson(String text) {
        return text.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n");
    }
}
