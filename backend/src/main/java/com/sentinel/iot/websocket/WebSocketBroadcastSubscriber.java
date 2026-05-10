package com.sentinel.iot.websocket;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Receives messages from the Redis pub/sub broadcast channel and fans them out
 * to all WebSocket sessions connected to THIS backend instance.
 *
 * Wired as a MessageListenerAdapter target in RedisWebSocketConfig.
 * Method name "onMessage" must match the delegate method configured there.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class WebSocketBroadcastSubscriber {

    private final TelemetryWebSocketHandler webSocketHandler;

    public void onMessage(String message) {
        log.trace("Redis ws-broadcast received — forwarding to {} local sessions", message.length());
        webSocketHandler.broadcastLocal(message);
    }
}
