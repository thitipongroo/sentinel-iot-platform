package com.sentinel.iot.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

@Service
@Slf4j
public class NotificationService {

    @Value("${notification.line.token}")
    private String lineToken;

    @Value("${notification.line.enabled}")
    private boolean enabled;

    private final RestTemplate restTemplate = new RestTemplate();
    private static final String LINE_NOTIFY_URL = "https://notify-api.line.me/api/notify";

    public void send(String message) {
        if (!enabled || lineToken == null || lineToken.isBlank()) {
            log.debug("LINE Notify disabled or token not set. Message: {}", message);
            return;
        }
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
            headers.setBearerAuth(lineToken);

            HttpEntity<String> entity = new HttpEntity<>("message=" + message, headers);
            ResponseEntity<String> resp = restTemplate.exchange(LINE_NOTIFY_URL, HttpMethod.POST, entity, String.class);
            log.info("LINE Notify sent. Status: {}", resp.getStatusCode());
        } catch (Exception e) {
            log.error("Failed to send LINE Notify: {}", e.getMessage());
        }
    }
}
