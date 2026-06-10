package com.sentinel.iot;

import com.sentinel.iot.websocket.WebSocketBroadcastPublisher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@Tag("unit")
@DisplayName("WebSocketBroadcastPublisher")
@ExtendWith(MockitoExtension.class)
class WebSocketBroadcastPublisherTest {

    @Mock StringRedisTemplate redisTemplate;

    WebSocketBroadcastPublisher publisher;

    @BeforeEach
    void setUp() {
        publisher = new WebSocketBroadcastPublisher(redisTemplate);
        ReflectionTestUtils.setField(publisher, "channel", "ws:telemetry");
    }

    // ── Envelope format ───────────────────────────────────────────────────────

    @Nested
    @DisplayName("Envelope format")
    class EnvelopeFormat {

        @SuppressWarnings("null")
        @Test
        @DisplayName("publish sends '<orgId>|<payload>' to the configured Redis channel")
        void publish_sendsEnvelopeToRedisChannel() {
            UUID orgId   = UUID.fromString("aaaaaaaa-0000-0000-0000-000000000001");
            String payload = "{\"deviceId\":\"sensor-1\",\"temperature\":45.0}";

            publisher.publish(orgId, payload);

            verify(redisTemplate).convertAndSend(
                    eq("ws:telemetry"),
                    eq(orgId + "|" + payload));
        }

        @SuppressWarnings("null")
        @Test
        @DisplayName("envelope is exactly '<orgId>|<payload>' with no extra transformation")
        void publish_envelopeFormat_isOrgIdPipePayload() {
            UUID orgId   = UUID.randomUUID();
            String payload = "raw-payload";

            publisher.publish(orgId, payload);

            verify(redisTemplate).convertAndSend(eq("ws:telemetry"), eq(orgId + "|" + payload));
        }
    }

    // ── Error handling ────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Error handling")
    class ErrorHandling {

        @SuppressWarnings("null")
        @Test
        @DisplayName("Redis exception does not propagate — publish is fail-open")
        void publish_handlesRedisExceptionGracefully() {
            UUID orgId = UUID.randomUUID();
            doThrow(new RuntimeException("Redis connection refused"))
                    .when(redisTemplate).convertAndSend(anyString(), anyString());

            assertThatNoException()
                    .as("Redis failure must not propagate from publish()")
                    .isThrownBy(() -> publisher.publish(orgId, "payload"));
        }
    }
}
