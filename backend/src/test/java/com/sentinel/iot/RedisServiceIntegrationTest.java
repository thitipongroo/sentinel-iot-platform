package com.sentinel.iot;

import com.sentinel.iot.service.RedisService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("RedisService — integration")
class RedisServiceIntegrationTest extends BaseIntegrationTest {

    @Autowired
    RedisService redisService;

    // ── Device status ─────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Device status")
    class DeviceStatus {

        @Test
        @DisplayName("setDeviceStatus and getDeviceStatus round-trip the value correctly")
        void setAndGetDeviceStatus_roundtrips() {
            String deviceId = UUID.randomUUID().toString();

            redisService.setDeviceStatus(deviceId, "ONLINE");

            assertThat(redisService.getDeviceStatus(deviceId))
                    .as("stored device status").isEqualTo("ONLINE");
        }

        @Test
        @DisplayName("setDeviceStatus can overwrite a previously stored status")
        void setDeviceStatus_canBeOverwritten() {
            String deviceId = UUID.randomUUID().toString();

            redisService.setDeviceStatus(deviceId, "ONLINE");
            redisService.setDeviceStatus(deviceId, "OFFLINE");

            assertThat(redisService.getDeviceStatus(deviceId))
                    .as("overwritten device status").isEqualTo("OFFLINE");
        }

        @Test
        @DisplayName("getDeviceStatus returns null for a device that has never been registered")
        void getDeviceStatus_returnsNullForUnknownDevice() {
            assertThat(redisService.getDeviceStatus(UUID.randomUUID().toString()))
                    .as("status for unknown device").isNull();
        }
    }

    // ── Latest telemetry ──────────────────────────────────────────────────────

    @Nested
    @DisplayName("Latest telemetry")
    class LatestTelemetry {

        @Test
        @DisplayName("setLatestTelemetry and getLatestTelemetry round-trip all sensor fields")
        void setAndGetLatestTelemetry_roundtrips() {
            String deviceId = UUID.randomUUID().toString();

            redisService.setLatestTelemetry(deviceId, 72.5, 55.0, true, 120.0);

            Map<Object, Object> result = redisService.getLatestTelemetry(deviceId);
            assertThat(result).as("telemetry map").isNotEmpty();
            assertThat(result.get("temperature")).as("temperature").isEqualTo("72.5");
            assertThat(result.get("humidity")).as("humidity").isEqualTo("55.0");
            assertThat(result.get("motion")).as("motion").isEqualTo("true");
            assertThat(result.get("smokePpm")).as("smokePpm").isEqualTo("120.0");
            assertThat(result.get("ts")).as("timestamp field").isNotNull();
        }

        @Test
        @DisplayName("null optional fields (motion, smokePpm) are stored with safe default values")
        void setLatestTelemetry_withNullOptionalFields_storesDefaults() {
            String deviceId = UUID.randomUUID().toString();

            redisService.setLatestTelemetry(deviceId, 30.0, 40.0, null, null);

            Map<Object, Object> result = redisService.getLatestTelemetry(deviceId);
            assertThat(result.get("motion")).as("default motion").isEqualTo("false");
            assertThat(result.get("smokePpm")).as("default smokePpm").isEqualTo("0.0");
        }

        @Test
        @DisplayName("getLatestTelemetry returns an empty map for a device with no stored reading")
        void getLatestTelemetry_returnsEmptyMapForUnknownDevice() {
            Map<Object, Object> result = redisService.getLatestTelemetry(UUID.randomUUID().toString());
            assertThat(result).as("telemetry for unknown device").isEmpty();
        }

        @Test
        @DisplayName("a subsequent setLatestTelemetry call overwrites the previous reading")
        void setLatestTelemetry_overwritesPreviousReading() {
            String deviceId = UUID.randomUUID().toString();

            redisService.setLatestTelemetry(deviceId, 50.0, 50.0, false, 0.0);
            redisService.setLatestTelemetry(deviceId, 90.0, 80.0, true, 300.0);

            Map<Object, Object> result = redisService.getLatestTelemetry(deviceId);
            assertThat(result.get("temperature")).as("updated temperature").isEqualTo("90.0");
            assertThat(result.get("smokePpm")).as("updated smokePpm").isEqualTo("300.0");
        }
    }
}
