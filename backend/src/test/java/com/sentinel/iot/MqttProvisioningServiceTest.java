package com.sentinel.iot;

import com.sentinel.iot.model.Device;
import com.sentinel.iot.model.DeviceLifecycleStatus;
import com.sentinel.iot.repository.DeviceRepository;
import com.sentinel.iot.service.MqttProvisioningService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@Tag("unit")
@DisplayName("MqttProvisioningService")
@ExtendWith(MockitoExtension.class)
class MqttProvisioningServiceTest {

    @Mock
    private DeviceRepository deviceRepository;

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @InjectMocks
    private MqttProvisioningService service;

    private Device device;
    private final UUID deviceId = UUID.randomUUID();
    private final UUID orgId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        device = new Device();
        device.setId(deviceId);
        device.setName("test-sensor");
        device.setOrganizationId(orgId);
        device.setLifecycleStatus(DeviceLifecycleStatus.ACTIVE);

        ReflectionTestUtils.setField(service, "fileProvisioningEnabled", false);
    }

    @Test
    @DisplayName("provisionDevice sets mqtt username, password hash on Device and saves to repository and Redis")
    void provisionDevice_updatesDeviceAndRedis() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(deviceRepository.save(any(Device.class))).thenAnswer(inv -> inv.getArgument(0));

        String username = service.provisionDevice(device, "securePass123");

        assertThat(username).isEqualTo("test-sensor");
        assertThat(device.getMqttUsername()).isEqualTo("test-sensor");
        assertThat(device.getMqttPasswordHash()).isNotBlank();
        verify(deviceRepository).save(device);
        verify(valueOperations).set(eq("mqtt:auth:test-sensor"), anyString());
        verify(valueOperations).set(eq("mqtt:acl:test-sensor"), contains("tenants/" + orgId));
        verify(valueOperations).set(eq("mqtt:device:test-sensor"), eq(deviceId.toString()));
        verify(valueOperations).set(eq("mqtt:org:test-sensor"), eq(orgId.toString()));
    }

    @Test
    @DisplayName("revokeCredentials clears DB password hash and evicts Redis auth/acl/device keys")
    void revokeCredentials_clearsDatabaseAndRedis() {
        device.setMqttUsername("test-sensor");
        device.setMqttPasswordHash("hashed");

        service.revokeCredentials(device);

        assertThat(device.getMqttPasswordHash()).isNull();
        verify(deviceRepository).save(device);
        verify(redisTemplate).delete(eq(List.of(
                "mqtt:auth:test-sensor",
                "mqtt:acl:test-sensor",
                "mqtt:device:test-sensor",
                "mqtt:org:test-sensor"
        )));
    }

    @Test
    @DisplayName("reprovisionDevice generates fresh credentials and provisions to DB & Redis")
    void reprovisionDevice_generatesNewCredentials() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(deviceRepository.save(any(Device.class))).thenAnswer(inv -> inv.getArgument(0));

        String rawPassword = service.reprovisionDevice(device);

        assertThat(rawPassword).isNotBlank();
        assertThat(device.getMqttPasswordHash()).isNotBlank();
        verify(deviceRepository).save(device);
        verify(valueOperations).set(eq("mqtt:auth:test-sensor"), anyString());
    }

    @Test
    @DisplayName("verifyCredentials returns true when password matches Redis cached hash")
    void verifyCredentials_matchesViaRedis() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("mqtt:auth:test-sensor")).thenReturn("2bb80d537b1da3e38bd30361aa855686bde0eacd7162fef6a25fe97bf527a25b"); // sha256 of "secret"

        boolean valid = service.verifyCredentials("test-sensor", "secret");

        assertThat(valid).isTrue();
    }

    @Test
    @DisplayName("verifyCredentials falls back to database when Redis entry is missing and repopulates Redis")
    void verifyCredentials_fallsBackToDatabase() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("mqtt:auth:test-sensor")).thenReturn(null);
        device.setMqttPasswordHash("2bb80d537b1da3e38bd30361aa855686bde0eacd7162fef6a25fe97bf527a25b"); // sha256 of "secret"
        when(deviceRepository.findByMqttUsername("test-sensor")).thenReturn(Optional.of(device));

        boolean valid = service.verifyCredentials("test-sensor", "secret");

        assertThat(valid).isTrue();
        verify(valueOperations).set(eq("mqtt:auth:test-sensor"), eq("2bb80d537b1da3e38bd30361aa855686bde0eacd7162fef6a25fe97bf527a25b"));
    }

    @Test
    @DisplayName("verifyCredentials rejects when device is DECOMMISSIONED")
    void verifyCredentials_rejectsWhenDeviceDecommissioned() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("mqtt:auth:test-sensor")).thenReturn(null);
        device.setLifecycleStatus(DeviceLifecycleStatus.DECOMMISSIONED);
        device.setMqttPasswordHash("2bb80d537b1da3e38bd30361aa855686bde0eacd7162fef6a25fe97bf527a25b");
        when(deviceRepository.findByMqttUsername("test-sensor")).thenReturn(Optional.of(device));

        boolean valid = service.verifyCredentials("test-sensor", "secret");

        assertThat(valid).isFalse();
    }

    @Test
    @DisplayName("verifyCredentials rejects when device is INACTIVE")
    void verifyCredentials_rejectsWhenDeviceInactive() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("mqtt:auth:test-sensor")).thenReturn(null);
        device.setLifecycleStatus(DeviceLifecycleStatus.INACTIVE);
        device.setMqttPasswordHash("2bb80d537b1da3e38bd30361aa855686bde0eacd7162fef6a25fe97bf527a25b");
        when(deviceRepository.findByMqttUsername("test-sensor")).thenReturn(Optional.of(device));

        boolean valid = service.verifyCredentials("test-sensor", "secret");

        assertThat(valid).isFalse();
    }

    // ── ACL Tests ─────────────────────────────────────────────────────────────

    @Test
    @DisplayName("verifyAcl allows publish to legacy factory/telemetry and tenant telemetry")
    void verifyAcl_allowsPublishTelemetry() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("mqtt:org:test-sensor")).thenReturn(orgId.toString());
        when(valueOperations.get("mqtt:device:test-sensor")).thenReturn(deviceId.toString());

        assertThat(service.verifyAcl("test-sensor", "client-1", "factory/telemetry", "publish")).isTrue();
        assertThat(service.verifyAcl("test-sensor", "client-1", "tenants/" + orgId + "/telemetry", "publish")).isTrue();
        // But rejects subscribe on global telemetry
        assertThat(service.verifyAcl("test-sensor", "client-1", "factory/telemetry", "subscribe")).isFalse();
        assertThat(service.verifyAcl("test-sensor", "client-1", "tenants/" + orgId + "/telemetry", "subscribe")).isFalse();
    }

    @Test
    @DisplayName("verifyAcl allows publish and subscribe to device-specific topics")
    void verifyAcl_allowsDeviceSpecificTopics() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("mqtt:org:test-sensor")).thenReturn(orgId.toString());
        when(valueOperations.get("mqtt:device:test-sensor")).thenReturn(deviceId.toString());

        String devTopic = "tenants/" + orgId + "/devices/" + deviceId + "/commands/reboot";
        assertThat(service.verifyAcl("test-sensor", "client-1", devTopic, "subscribe")).isTrue();
        assertThat(service.verifyAcl("test-sensor", "client-1", devTopic, "publish")).isTrue();

        String namedTopic = "tenants/" + orgId + "/devices/test-sensor/shadow/get";
        assertThat(service.verifyAcl("test-sensor", "client-1", namedTopic, "subscribe")).isTrue();
        assertThat(service.verifyAcl("test-sensor", "client-1", namedTopic, "publish")).isTrue();
    }

    @Test
    @DisplayName("verifyAcl rejects cross-tenant topic access")
    void verifyAcl_rejectsCrossTenantTopic() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("mqtt:org:test-sensor")).thenReturn(orgId.toString());
        when(valueOperations.get("mqtt:device:test-sensor")).thenReturn(deviceId.toString());

        UUID foreignOrgId = UUID.randomUUID();
        String spoofedTopic = "tenants/" + foreignOrgId + "/devices/" + deviceId + "/telemetry";
        assertThat(service.verifyAcl("test-sensor", "client-1", spoofedTopic, "publish")).isFalse();

        String wildcardTopic = "tenants/#";
        assertThat(service.verifyAcl("test-sensor", "client-1", wildcardTopic, "subscribe")).isFalse();
    }

    @Test
    @DisplayName("verifyAcl falls back to DB when Redis cache is absent and rejects inactive devices")
    void verifyAcl_fallsBackToDbAndRejectsInactive() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("mqtt:org:test-sensor")).thenReturn(null);
        when(valueOperations.get("mqtt:device:test-sensor")).thenReturn(null);

        device.setLifecycleStatus(DeviceLifecycleStatus.DECOMMISSIONED);
        when(deviceRepository.findByMqttUsername("test-sensor")).thenReturn(Optional.of(device));

        boolean allowed = service.verifyAcl("test-sensor", "client-1", "tenants/" + orgId + "/telemetry", "publish");
        assertThat(allowed).isFalse();
    }
}

