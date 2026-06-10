package com.sentinel.iot;

import com.sentinel.iot.websocket.WebSocketBroadcastPublisher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WebSocketBroadcastPublisherTest {

    @Mock StringRedisTemplate redisTemplate;

    WebSocketBroadcastPublisher publisher;

    @BeforeEach
    void setUp() {
        publisher = new WebSocketBroadcastPublisher(redisTemplate);
        ReflectionTestUtils.setField(publisher, "channel", "ws:telemetry");
    }

    @SuppressWarnings("null")
    @Test
    void publish_sendsEnvelopeToRedisChannel() {
        UUID orgId = UUID.fromString("aaaaaaaa-0000-0000-0000-000000000001");
        String payload = "{\"deviceId\":\"sensor-1\",\"temperature\":45.0}";

        publisher.publish(orgId, payload);

        verify(redisTemplate).convertAndSend(
                eq("ws:telemetry"),
                eq(orgId + "|" + payload));
    }

    @SuppressWarnings("null")
    @Test
    void publish_envelopeFormat_isOrgIdPipePayload() {
        UUID orgId = UUID.randomUUID();
        String payload = "raw-payload";
        String expected = orgId + "|" + payload;

        publisher.publish(orgId, payload);

        verify(redisTemplate).convertAndSend(eq("ws:telemetry"), eq(expected));
    }

    @SuppressWarnings("null")
    @Test
    void publish_handlesRedisExceptionGracefully() {
        UUID orgId = UUID.randomUUID();
        doThrow(new RuntimeException("Redis connection refused"))
                .when(redisTemplate).convertAndSend(anyString(), anyString());

        assertThatNoException().isThrownBy(() -> publisher.publish(orgId, "payload"));
    }
}
