package com.sentinel.iot;

import com.sentinel.iot.service.RedisService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class RedisServiceIntegrationTest extends BaseIntegrationTest {

    @Autowired
    RedisService redisService;

    @Test
    void setAndGetDeviceStatus_roundtrips() {
        String deviceId = UUID.randomUUID().toString();

        redisService.setDeviceStatus(deviceId, "ONLINE");

        assertThat(redisService.getDeviceStatus(deviceId)).isEqualTo("ONLINE");
    }

    @Test
    void setDeviceStatus_canBeOverwritten() {
        String deviceId = UUID.randomUUID().toString();

        redisService.setDeviceStatus(deviceId, "ONLINE");
        redisService.setDeviceStatus(deviceId, "OFFLINE");

        assertThat(redisService.getDeviceStatus(deviceId)).isEqualTo("OFFLINE");
    }

    @Test
    void getDeviceStatus_returnsNullForUnknownDevice() {
        assertThat(redisService.getDeviceStatus(UUID.randomUUID().toString())).isNull();
    }

    @Test
    void setAndGetLatestTelemetry_roundtrips() {
        String deviceId = UUID.randomUUID().toString();

        redisService.setLatestTelemetry(deviceId, 72.5, 55.0, true, 120.0);

        Map<Object, Object> result = redisService.getLatestTelemetry(deviceId);
        assertThat(result).isNotEmpty();
        assertThat(result.get("temperature")).isEqualTo("72.5");
        assertThat(result.get("humidity")).isEqualTo("55.0");
        assertThat(result.get("motion")).isEqualTo("true");
        assertThat(result.get("smokePpm")).isEqualTo("120.0");
        assertThat(result.get("ts")).isNotNull();
    }

    @Test
    void setLatestTelemetry_withNullOptionalFields_storesDefaults() {
        String deviceId = UUID.randomUUID().toString();

        redisService.setLatestTelemetry(deviceId, 30.0, 40.0, null, null);

        Map<Object, Object> result = redisService.getLatestTelemetry(deviceId);
        assertThat(result.get("motion")).isEqualTo("false");
        assertThat(result.get("smokePpm")).isEqualTo("0.0");
    }

    @Test
    void getLatestTelemetry_returnsEmptyMapForUnknownDevice() {
        Map<Object, Object> result = redisService.getLatestTelemetry(UUID.randomUUID().toString());
        assertThat(result).isEmpty();
    }

    @Test
    void setLatestTelemetry_overwritesPreviousReading() {
        String deviceId = UUID.randomUUID().toString();

        redisService.setLatestTelemetry(deviceId, 50.0, 50.0, false, 0.0);
        redisService.setLatestTelemetry(deviceId, 90.0, 80.0, true, 300.0);

        Map<Object, Object> result = redisService.getLatestTelemetry(deviceId);
        assertThat(result.get("temperature")).isEqualTo("90.0");
        assertThat(result.get("smokePpm")).isEqualTo("300.0");
    }
}
