package com.sentinel.iot;

import com.sentinel.iot.model.Device;
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
    @DisplayName("verifyCredentials falls back to database when Redis entry is missing")
    void verifyCredentials_fallsBackToDatabase() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("mqtt:auth:test-sensor")).thenReturn(null);
        device.setMqttPasswordHash("2bb80d537b1da3e38bd30361aa855686bde0eacd7162fef6a25fe97bf527a25b"); // sha256 of "secret"
        when(deviceRepository.findByMqttUsername("test-sensor")).thenReturn(Optional.of(device));

        boolean valid = service.verifyCredentials("test-sensor", "secret");

        assertThat(valid).isTrue();
    }
}
