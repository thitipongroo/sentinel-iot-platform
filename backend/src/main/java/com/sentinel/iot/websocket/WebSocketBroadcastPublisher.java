package com.sentinel.iot.websocket;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * Publishes a telemetry payload to the Redis pub/sub broadcast channel.
 *
 * All backend instances subscribe to this channel via WebSocketBroadcastSubscriber.
 * Each subscriber fans the message out to its own local WebSocket sessions for the
 * matching tenant — preventing cross-tenant data leakage via the live dashboard.
 *
 * If Redis pub/sub is unavailable, falls back to broadcasting directly to this local
 * instance's WebSocket sessions so the dashboard stays live.
 *
 * Message format: {@code <orgId>|<rawPayload>}
 * The handler strips the prefix before forwarding to the client.
 */
@Service
@Slf4j
public class WebSocketBroadcastPublisher {

    private final StringRedisTemplate redisTemplate;
    private final TelemetryWebSocketHandler webSocketHandler;

    @Value("${ws.broadcast.channel:ws:telemetry}")
    private String channel;

    public WebSocketBroadcastPublisher(StringRedisTemplate redisTemplate) {
        this(redisTemplate, null);
    }

    @Autowired
    public WebSocketBroadcastPublisher(StringRedisTemplate redisTemplate,
                                       @Autowired(required = false) TelemetryWebSocketHandler webSocketHandler) {
        this.redisTemplate = redisTemplate;
        this.webSocketHandler = webSocketHandler;
    }

    @SuppressWarnings("null")
    public void publish(UUID organizationId, String rawPayload) {
        String envelope = organizationId + "|" + rawPayload;
        try {
            redisTemplate.convertAndSend(channel, envelope);
        } catch (Exception e) {
            log.warn("Redis WebSocket broadcast failed — falling back to local instance broadcast: {}", e.getMessage());
            if (webSocketHandler != null) {
                try {
                    webSocketHandler.broadcastLocal(envelope);
                } catch (Exception ex) {
                    log.error("Local WebSocket broadcast fallback failed: {}", ex.getMessage());
                }
            }
        }
    }
}

