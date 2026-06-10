package com.sentinel.iot;

import com.sentinel.iot.dto.DeviceCapabilityRequest;
import com.sentinel.iot.dto.DeviceLifecycleRequest;
import com.sentinel.iot.dto.DeviceRequest;
import com.sentinel.iot.dto.FirmwareUpdateRequest;
import com.sentinel.iot.model.Device;
import com.sentinel.iot.model.DeviceLifecycleStatus;
import com.sentinel.iot.model.SensorCapability;
import com.sentinel.iot.repository.DeviceRepository;
import com.sentinel.iot.security.TenantContext;
import com.sentinel.iot.service.DeviceService;
import com.sentinel.iot.service.RedisService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DeviceServiceTest {

    @Mock DeviceRepository deviceRepository;
    @Mock RedisService redisService;
    @InjectMocks DeviceService deviceService;

    private final UUID orgId    = UUID.fromString("aaaaaaaa-0000-0000-0000-000000000001");
    private final UUID deviceId = UUID.fromString("dddddddd-0000-0000-0000-000000000001");

    private DeviceRequest request;

    @BeforeEach
    void setUp() {
        request = new DeviceRequest();
        request.setName("sensor-1");
        request.setDescription("Test sensor");
        request.setLocation("Factory A");
    }

    @AfterEach
    void clearTenant() {
        TenantContext.clear();
    }

    // ---- create ------------------------------------------------------------

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

    @SuppressWarnings("null")
    @Test
    void create_withTenantContext_checksByOrgScoped() {
        TenantContext.set(orgId);
        when(deviceRepository.existsByNameAndOrganizationId("sensor-1", orgId)).thenReturn(false);
        Device saved = new Device();
        saved.setId(UUID.randomUUID());
        saved.setName("sensor-1");
        saved.setStatus("OFFLINE");
        when(deviceRepository.save(any())).thenReturn(saved);

        deviceService.create(request);

        verify(deviceRepository).existsByNameAndOrganizationId("sensor-1", orgId);
        verify(deviceRepository, never()).existsByName(any());
    }

    // ---- findAll -----------------------------------------------------------

    @SuppressWarnings("null")
    @Test
    void findAll_withOrgId_queriesByOrganization() {
        TenantContext.set(orgId);
        Device d = new Device();
        d.setId(deviceId);
        d.setStatus("OFFLINE");
        when(deviceRepository.findAllByOrganizationId(orgId)).thenReturn(List.of(d));
        when(redisService.getDeviceStatus(deviceId.toString())).thenReturn("ONLINE");

        List<Device> result = deviceService.findAll();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getStatus()).isEqualTo("ONLINE");
        verify(deviceRepository).findAllByOrganizationId(orgId);
    }

    @Test
    void findAll_withNullOrgId_queriesAll() {
        when(deviceRepository.findAll()).thenReturn(List.of());

        deviceService.findAll();

        verify(deviceRepository).findAll();
        verify(deviceRepository, never()).findAllByOrganizationId(any());
    }

    @SuppressWarnings("null")
    @Test
    void findAll_keepsPersistedStatus_whenRedisReturnsNull() {
        TenantContext.set(orgId);
        Device d = new Device();
        d.setId(deviceId);
        d.setStatus("ONLINE");
        when(deviceRepository.findAllByOrganizationId(orgId)).thenReturn(List.of(d));
        when(redisService.getDeviceStatus(any())).thenReturn(null);

        List<Device> result = deviceService.findAll();

        assertThat(result.get(0).getStatus()).isEqualTo("ONLINE");
    }

    // ---- findById ----------------------------------------------------------

    @SuppressWarnings("null")
    @Test
    void findById_withOrgId_usesScopedLookup() {
        TenantContext.set(orgId);
        Device d = new Device();
        d.setId(deviceId);
        when(deviceRepository.findByIdAndOrganizationId(deviceId, orgId)).thenReturn(Optional.of(d));

        Device result = deviceService.findById(deviceId);

        assertThat(result).isSameAs(d);
        verify(deviceRepository).findByIdAndOrganizationId(deviceId, orgId);
    }

    @SuppressWarnings("null")
    @Test
    void findById_withNullOrgId_usesUnscopedLookup() {
        Device d = new Device();
        d.setId(deviceId);
        when(deviceRepository.findById(deviceId)).thenReturn(Optional.of(d));

        deviceService.findById(deviceId);

        verify(deviceRepository).findById(deviceId);
        verify(deviceRepository, never()).findByIdAndOrganizationId(any(), any());
    }

    @SuppressWarnings("null")
    @Test
    void findById_enrichesStatusFromRedis_whenCacheHasValue() {
        Device d = new Device();
        d.setId(deviceId);
        d.setStatus("OFFLINE");
        when(deviceRepository.findById(deviceId)).thenReturn(Optional.of(d));
        when(redisService.getDeviceStatus(deviceId.toString())).thenReturn("ONLINE");

        Device result = deviceService.findById(deviceId);

        assertThat(result.getStatus()).isEqualTo("ONLINE");
    }

    @SuppressWarnings("null")
    @Test
    void findById_throws_whenDeviceNotFound() {
        when(deviceRepository.findById(deviceId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> deviceService.findById(deviceId))
                .isInstanceOf(NoSuchElementException.class)
                .hasMessageContaining(deviceId.toString());
    }

    // ---- updateStatus ------------------------------------------------------

    @SuppressWarnings("null")
    @Test
    void updateStatus_updatesDeviceAndRedis() {
        Device d = new Device();
        d.setId(deviceId);
        d.setStatus("OFFLINE");
        when(deviceRepository.findById(deviceId)).thenReturn(Optional.of(d));
        when(deviceRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Device result = deviceService.updateStatus(deviceId, "ONLINE");

        assertThat(result.getStatus()).isEqualTo("ONLINE");
        verify(redisService).setDeviceStatus(deviceId.toString(), "ONLINE");
    }

    // ---- updateLifecycle ---------------------------------------------------

    @SuppressWarnings("null")
    @Test
    void updateLifecycle_toActive_setsLifecycleWithoutForcingOffline() {
        Device d = new Device();
        d.setId(deviceId);
        d.setLifecycleStatus(DeviceLifecycleStatus.PROVISIONED);
        d.setStatus("ONLINE");
        when(deviceRepository.findById(deviceId)).thenReturn(Optional.of(d));
        when(deviceRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        DeviceLifecycleRequest req = new DeviceLifecycleRequest();
        req.setLifecycleStatus(DeviceLifecycleStatus.ACTIVE);

        Device result = deviceService.updateLifecycle(deviceId, req);

        assertThat(result.getLifecycleStatus()).isEqualTo(DeviceLifecycleStatus.ACTIVE);
        assertThat(result.getStatus()).isEqualTo("ONLINE");
        verify(redisService, never()).setDeviceStatus(any(), any());
    }

    @SuppressWarnings("null")
    @Test
    void updateLifecycle_toDecommissioned_forcesDeviceOffline() {
        Device d = new Device();
        d.setId(deviceId);
        d.setLifecycleStatus(DeviceLifecycleStatus.ACTIVE);
        d.setStatus("ONLINE");
        when(deviceRepository.findById(deviceId)).thenReturn(Optional.of(d));
        when(deviceRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        DeviceLifecycleRequest req = new DeviceLifecycleRequest();
        req.setLifecycleStatus(DeviceLifecycleStatus.DECOMMISSIONED);

        Device result = deviceService.updateLifecycle(deviceId, req);

        assertThat(result.getStatus()).isEqualTo("OFFLINE");
        verify(redisService).setDeviceStatus(deviceId.toString(), "OFFLINE");
    }

    @SuppressWarnings("null")
    @Test
    void updateLifecycle_toInactive_forcesDeviceOffline() {
        Device d = new Device();
        d.setId(deviceId);
        d.setLifecycleStatus(DeviceLifecycleStatus.ACTIVE);
        d.setStatus("ONLINE");
        when(deviceRepository.findById(deviceId)).thenReturn(Optional.of(d));
        when(deviceRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        DeviceLifecycleRequest req = new DeviceLifecycleRequest();
        req.setLifecycleStatus(DeviceLifecycleStatus.INACTIVE);

        Device result = deviceService.updateLifecycle(deviceId, req);

        assertThat(result.getStatus()).isEqualTo("OFFLINE");
        verify(redisService).setDeviceStatus(deviceId.toString(), "OFFLINE");
    }

    @SuppressWarnings("null")
    @Test
    void updateLifecycle_throwsWhenAlreadyDecommissioned() {
        Device d = new Device();
        d.setId(deviceId);
        d.setLifecycleStatus(DeviceLifecycleStatus.DECOMMISSIONED);
        when(deviceRepository.findById(deviceId)).thenReturn(Optional.of(d));

        DeviceLifecycleRequest req = new DeviceLifecycleRequest();
        req.setLifecycleStatus(DeviceLifecycleStatus.ACTIVE);

        assertThatThrownBy(() -> deviceService.updateLifecycle(deviceId, req))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("decommissioned");
    }

    // ---- updateFirmware ----------------------------------------------------

    @SuppressWarnings("null")
    @Test
    void updateFirmware_setsFirmwareVersionAndTimestamp() {
        Device d = new Device();
        d.setId(deviceId);
        d.setLifecycleStatus(DeviceLifecycleStatus.ACTIVE);
        when(deviceRepository.findById(deviceId)).thenReturn(Optional.of(d));
        when(deviceRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        FirmwareUpdateRequest req = new FirmwareUpdateRequest();
        req.setFirmwareVersion("2.3.1");

        Device result = deviceService.updateFirmware(deviceId, req);

        assertThat(result.getFirmwareVersion()).isEqualTo("2.3.1");
        assertThat(result.getFirmwareUpdatedAt()).isNotNull();
    }

    @SuppressWarnings("null")
    @Test
    void updateFirmware_throwsWhenDeviceIsDecommissioned() {
        Device d = new Device();
        d.setId(deviceId);
        d.setLifecycleStatus(DeviceLifecycleStatus.DECOMMISSIONED);
        when(deviceRepository.findById(deviceId)).thenReturn(Optional.of(d));

        FirmwareUpdateRequest req = new FirmwareUpdateRequest();
        req.setFirmwareVersion("2.3.1");

        assertThatThrownBy(() -> deviceService.updateFirmware(deviceId, req))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("decommissioned");
    }

    // ---- updateCapabilities ------------------------------------------------

    @SuppressWarnings("null")
    @Test
    void updateCapabilities_replacesCapabilityMap() {
        Device d = new Device();
        d.setId(deviceId);
        d.setLifecycleStatus(DeviceLifecycleStatus.ACTIVE);
        when(deviceRepository.findById(deviceId)).thenReturn(Optional.of(d));
        when(deviceRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Map<String, SensorCapability> caps = Map.of(
                "TEMPERATURE", SensorCapability.above("°C", 75.0, 90.0, 1));
        DeviceCapabilityRequest req = new DeviceCapabilityRequest(caps);

        Device result = deviceService.updateCapabilities(deviceId, req);

        assertThat(result.getCapabilities()).containsKey("TEMPERATURE");
    }

    @SuppressWarnings("null")
    @Test
    void updateCapabilities_throwsWhenDeviceIsDecommissioned() {
        Device d = new Device();
        d.setId(deviceId);
        d.setLifecycleStatus(DeviceLifecycleStatus.DECOMMISSIONED);
        when(deviceRepository.findById(deviceId)).thenReturn(Optional.of(d));

        DeviceCapabilityRequest req = new DeviceCapabilityRequest(Map.of());

        assertThatThrownBy(() -> deviceService.updateCapabilities(deviceId, req))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("decommissioned");
    }
}
