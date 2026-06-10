package com.sentinel.iot;

import com.sentinel.iot.websocket.TelemetryWebSocketHandler;
import com.sentinel.iot.websocket.WebSocketBroadcastSubscriber;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class WebSocketBroadcastSubscriberTest {

    @Mock TelemetryWebSocketHandler webSocketHandler;

    WebSocketBroadcastSubscriber subscriber;

    @BeforeEach
    void setUp() {
        subscriber = new WebSocketBroadcastSubscriber(webSocketHandler);
    }

    @Test
    void onMessage_forwardsToWebSocketHandlerBroadcastLocal() {
        String message = "aaaaaaaa-0000-0000-0000-000000000001|{\"deviceId\":\"sensor-1\"}";

        subscriber.onMessage(message);

        verify(webSocketHandler).broadcastLocal(message);
    }

    @Test
    void onMessage_forwardsRawMessage_withoutModification() {
        String rawMessage = "any-string-payload";

        subscriber.onMessage(rawMessage);

        verify(webSocketHandler).broadcastLocal(rawMessage);
    }
}
