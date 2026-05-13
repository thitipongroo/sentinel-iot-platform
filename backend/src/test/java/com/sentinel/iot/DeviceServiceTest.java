package com.sentinel.iot;

import com.sentinel.iot.dto.DeviceRequest;
import com.sentinel.iot.model.Device;
import com.sentinel.iot.repository.DeviceRepository;
import com.sentinel.iot.service.DeviceService;
import com.sentinel.iot.service.RedisService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DeviceServiceTest {

    @Mock DeviceRepository deviceRepository;
    @Mock RedisService redisService;
    @InjectMocks DeviceService deviceService;

    private DeviceRequest request;

    @BeforeEach
    void setUp() {
        request = new DeviceRequest();
        request.setName("sensor-1");
        request.setDescription("Test sensor");
        request.setLocation("Factory A");
    }

    @SuppressWarnings("null")
    @Test
    void create_shouldSaveAndReturnDevice() {
        when(deviceRepository.existsByName("sensor-1")).thenReturn(false);
        Device saved = new Device();
        saved.setId(UUID.randomUUID());
        saved.setName("sensor-1");
        saved.setStatus("OFFLINE");
        when(deviceRepository.save(any(Device.class))).thenReturn(saved);

        Device result = deviceService.create(request);

        assertThat(result.getName()).isEqualTo("sensor-1");
        assertThat(result.getStatus()).isEqualTo("OFFLINE");
        verify(redisService).setDeviceStatus(anyString(), eq("OFFLINE"));
    }

    @Test
    void create_shouldThrowWhenNameExists() {
        when(deviceRepository.existsByName("sensor-1")).thenReturn(true);
        assertThatThrownBy(() -> deviceService.create(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("already exists");
    }
}
