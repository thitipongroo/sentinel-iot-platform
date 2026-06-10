package com.sentinel.iot;

import com.sentinel.iot.service.notification.AlertDeduplicator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AlertDeduplicatorTest {

    @Mock StringRedisTemplate redis;
    @Mock ValueOperations<String, String> valueOps;

    AlertDeduplicator deduplicator;

    @SuppressWarnings("null")
    @BeforeEach
    void setUp() {
        deduplicator = new AlertDeduplicator(redis);
        ReflectionTestUtils.setField(deduplicator, "cooldownMinutes", 5L);
        ReflectionTestUtils.setField(deduplicator, "enabled", true);
        when(redis.opsForValue()).thenReturn(valueOps);
    }

    @SuppressWarnings("null")
    @Test
    void shouldSend_returnsTrueOnFirstCall() {
        when(valueOps.setIfAbsent(anyString(), eq("1"), any(Duration.class))).thenReturn(true);

        assertThat(deduplicator.shouldSend(UUID.randomUUID(), "TEMPERATURE", "CRITICAL")).isTrue();
    }

    @SuppressWarnings("null")
    @Test
    void shouldSend_returnsFalseWithinCooldown() {
        when(valueOps.setIfAbsent(anyString(), eq("1"), any(Duration.class))).thenReturn(false);

        assertThat(deduplicator.shouldSend(UUID.randomUUID(), "TEMPERATURE", "CRITICAL")).isFalse();
    }

    @SuppressWarnings("null")
    @Test
    void shouldSend_usesCorrectKeyFormat() {
        UUID deviceId = UUID.fromString("00000000-0000-0000-0000-000000000001");
        when(valueOps.setIfAbsent(anyString(), eq("1"), any(Duration.class))).thenReturn(true);

        deduplicator.shouldSend(deviceId, "SMOKE_PPM", "WARNING");

        String expectedKey = "alert:dedup:" + deviceId + ":SMOKE_PPM:WARNING";
        verify(valueOps).setIfAbsent(eq(expectedKey), eq("1"), eq(Duration.ofMinutes(5L)));
    }

    @SuppressWarnings("null")
    @Test
    void shouldSend_differentDeviceKeysAreIndependent() {
        UUID deviceA = UUID.randomUUID();
        UUID deviceB = UUID.randomUUID();
        when(valueOps.setIfAbsent(contains(deviceA.toString()), eq("1"), any())).thenReturn(true);
        when(valueOps.setIfAbsent(contains(deviceB.toString()), eq("1"), any())).thenReturn(false);

        assertThat(deduplicator.shouldSend(deviceA, "TEMPERATURE", "CRITICAL")).isTrue();
        assertThat(deduplicator.shouldSend(deviceB, "TEMPERATURE", "CRITICAL")).isFalse();
    }

    @SuppressWarnings("null")
    @Test
    void shouldSend_alwaysTrueWhenDisabled() {
        ReflectionTestUtils.setField(deduplicator, "enabled", false);

        assertThat(deduplicator.shouldSend(UUID.randomUUID(), "TEMPERATURE", "CRITICAL")).isTrue();
        verifyNoInteractions(redis);
    }

    @SuppressWarnings("null")
    @Test
    void shouldSend_failOpenWhenRedisThrows() {
        when(valueOps.setIfAbsent(anyString(), eq("1"), any(Duration.class)))
                .thenThrow(new RuntimeException("Redis connection refused"));

        assertThat(deduplicator.shouldSend(UUID.randomUUID(), "TEMPERATURE", "CRITICAL")).isTrue();
    }
}
