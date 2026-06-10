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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
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

@Tag("unit")
@DisplayName("DeviceService")
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

    // ── create ────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("create()")
    class Create {

        @SuppressWarnings("null")
        @Test
        @DisplayName("saves device and caches OFFLINE status in Redis")
        void create_savesAndReturnsDevice() {
            when(deviceRepository.existsByName("sensor-1")).thenReturn(false);
            Device saved = device("sensor-1", "OFFLINE");
            when(deviceRepository.save(any(Device.class))).thenReturn(saved);

            Device result = deviceService.create(request);

            assertThat(result.getName()).as("name").isEqualTo("sensor-1");
            assertThat(result.getStatus()).as("initial status").isEqualTo("OFFLINE");
            verify(redisService).setDeviceStatus(anyString(), eq("OFFLINE"));
        }

        @Test
        @DisplayName("throws IllegalArgumentException when name already exists (global scope)")
        void create_throwsWhenNameExists() {
            when(deviceRepository.existsByName("sensor-1")).thenReturn(true);

            assertThatThrownBy(() -> deviceService.create(request))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("already exists");
        }

        @SuppressWarnings("null")
        @Test
        @DisplayName("uses org-scoped duplicate check when tenant context is set")
        void create_withTenantContext_checksByOrgScoped() {
            TenantContext.set(orgId);
            when(deviceRepository.existsByNameAndOrganizationId("sensor-1", orgId)).thenReturn(false);
            Device saved = device("sensor-1", "OFFLINE");
            when(deviceRepository.save(any())).thenReturn(saved);

            deviceService.create(request);

            verify(deviceRepository).existsByNameAndOrganizationId("sensor-1", orgId);
            verify(deviceRepository, never()).existsByName(any());
        }
    }

    // ── findAll ───────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("findAll()")
    class FindAll {

        @SuppressWarnings("null")
        @Test
        @DisplayName("queries by organization when tenant context is set")
        void findAll_withOrgId_queriesByOrganization() {
            TenantContext.set(orgId);
            Device d = deviceWithId(deviceId, "OFFLINE");
            when(deviceRepository.findAllByOrganizationId(orgId)).thenReturn(List.of(d));
            when(redisService.getDeviceStatus(deviceId.toString())).thenReturn("ONLINE");

            List<Device> result = deviceService.findAll();

            assertThat(result).hasSize(1);
            assertThat(result.get(0).getStatus())
                    .as("Redis status should override persisted status")
                    .isEqualTo("ONLINE");
            verify(deviceRepository).findAllByOrganizationId(orgId);
        }

        @Test
        @DisplayName("queries all devices when no tenant context is set")
        void findAll_withNullOrgId_queriesAll() {
            when(deviceRepository.findAll()).thenReturn(List.of());

            deviceService.findAll();

            verify(deviceRepository).findAll();
            verify(deviceRepository, never()).findAllByOrganizationId(any());
        }

        @SuppressWarnings("null")
        @Test
        @DisplayName("keeps persisted status when Redis returns null (cache miss)")
        void findAll_keepsPersistedStatus_whenRedisReturnsNull() {
            TenantContext.set(orgId);
            Device d = deviceWithId(deviceId, "ONLINE");
            when(deviceRepository.findAllByOrganizationId(orgId)).thenReturn(List.of(d));
            when(redisService.getDeviceStatus(any())).thenReturn(null);

            List<Device> result = deviceService.findAll();

            assertThat(result.get(0).getStatus())
                    .as("persisted status should be preserved on cache miss")
                    .isEqualTo("ONLINE");
        }
    }

    // ── findById ──────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("findById()")
    class FindById {

        @SuppressWarnings("null")
        @Test
        @DisplayName("uses org-scoped lookup when tenant context is set")
        void findById_withOrgId_usesScopedLookup() {
            TenantContext.set(orgId);
            Device d = deviceWithId(deviceId, "OFFLINE");
            when(deviceRepository.findByIdAndOrganizationId(deviceId, orgId))
                    .thenReturn(Optional.of(d));

            Device result = deviceService.findById(deviceId);

            assertThat(result).isSameAs(d);
            verify(deviceRepository).findByIdAndOrganizationId(deviceId, orgId);
        }

        @SuppressWarnings("null")
        @Test
        @DisplayName("uses global lookup when no tenant context is set")
        void findById_withNullOrgId_usesUnscopedLookup() {
            Device d = deviceWithId(deviceId, "OFFLINE");
            when(deviceRepository.findById(deviceId)).thenReturn(Optional.of(d));

            deviceService.findById(deviceId);

            verify(deviceRepository).findById(deviceId);
            verify(deviceRepository, never()).findByIdAndOrganizationId(any(), any());
        }

        @SuppressWarnings("null")
        @Test
        @DisplayName("enriches status from Redis when cache contains a value")
        void findById_enrichesStatusFromRedis_whenCacheHasValue() {
            Device d = deviceWithId(deviceId, "OFFLINE");
            when(deviceRepository.findById(deviceId)).thenReturn(Optional.of(d));
            when(redisService.getDeviceStatus(deviceId.toString())).thenReturn("ONLINE");

            Device result = deviceService.findById(deviceId);

            assertThat(result.getStatus()).isEqualTo("ONLINE");
        }

        @SuppressWarnings("null")
        @Test
        @DisplayName("throws NoSuchElementException when device does not exist")
        void findById_throws_whenDeviceNotFound() {
            when(deviceRepository.findById(deviceId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> deviceService.findById(deviceId))
                    .isInstanceOf(NoSuchElementException.class)
                    .hasMessageContaining(deviceId.toString());
        }
    }

    // ── updateStatus ──────────────────────────────────────────────────────────

    @Nested
    @DisplayName("updateStatus()")
    class UpdateStatus {

        @SuppressWarnings("null")
        @Test
        @DisplayName("persists new status in DB and updates Redis cache")
        void updateStatus_updatesDeviceAndRedis() {
            Device d = deviceWithId(deviceId, "OFFLINE");
            when(deviceRepository.findById(deviceId)).thenReturn(Optional.of(d));
            when(deviceRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            Device result = deviceService.updateStatus(deviceId, "ONLINE");

            assertThat(result.getStatus()).isEqualTo("ONLINE");
            verify(redisService).setDeviceStatus(deviceId.toString(), "ONLINE");
        }
    }

    // ── updateLifecycle ───────────────────────────────────────────────────────

    @Nested
    @DisplayName("updateLifecycle()")
    class UpdateLifecycle {

        @SuppressWarnings("null")
        @Test
        @DisplayName("transitions to ACTIVE without forcing device offline")
        void updateLifecycle_toActive_doesNotForceOffline() {
            Device d = deviceWithLifecycle(DeviceLifecycleStatus.PROVISIONED, "ONLINE");
            when(deviceRepository.findById(deviceId)).thenReturn(Optional.of(d));
            when(deviceRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            Device result = deviceService.updateLifecycle(deviceId, lifecycleRequest(DeviceLifecycleStatus.ACTIVE));

            assertThat(result.getLifecycleStatus()).isEqualTo(DeviceLifecycleStatus.ACTIVE);
            assertThat(result.getStatus()).as("online status must not change").isEqualTo("ONLINE");
            verify(redisService, never()).setDeviceStatus(any(), any());
        }

        @SuppressWarnings("null")
        @Test
        @DisplayName("forces device OFFLINE when transitioning to DECOMMISSIONED")
        void updateLifecycle_toDecommissioned_forcesDeviceOffline() {
            Device d = deviceWithLifecycle(DeviceLifecycleStatus.ACTIVE, "ONLINE");
            when(deviceRepository.findById(deviceId)).thenReturn(Optional.of(d));
            when(deviceRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            Device result = deviceService.updateLifecycle(deviceId, lifecycleRequest(DeviceLifecycleStatus.DECOMMISSIONED));

            assertThat(result.getStatus()).isEqualTo("OFFLINE");
            verify(redisService).setDeviceStatus(deviceId.toString(), "OFFLINE");
        }

        @SuppressWarnings("null")
        @Test
        @DisplayName("forces device OFFLINE when transitioning to INACTIVE")
        void updateLifecycle_toInactive_forcesDeviceOffline() {
            Device d = deviceWithLifecycle(DeviceLifecycleStatus.ACTIVE, "ONLINE");
            when(deviceRepository.findById(deviceId)).thenReturn(Optional.of(d));
            when(deviceRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            Device result = deviceService.updateLifecycle(deviceId, lifecycleRequest(DeviceLifecycleStatus.INACTIVE));

            assertThat(result.getStatus()).isEqualTo("OFFLINE");
            verify(redisService).setDeviceStatus(deviceId.toString(), "OFFLINE");
        }

        @SuppressWarnings("null")
        @Test
        @DisplayName("throws when attempting to re-activate a DECOMMISSIONED device")
        void updateLifecycle_throwsWhenAlreadyDecommissioned() {
            Device d = deviceWithLifecycle(DeviceLifecycleStatus.DECOMMISSIONED, "OFFLINE");
            when(deviceRepository.findById(deviceId)).thenReturn(Optional.of(d));

            assertThatThrownBy(() -> deviceService.updateLifecycle(deviceId, lifecycleRequest(DeviceLifecycleStatus.ACTIVE)))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("decommissioned");
        }
    }

    // ── updateFirmware ────────────────────────────────────────────────────────

    @Nested
    @DisplayName("updateFirmware()")
    class UpdateFirmware {

        @SuppressWarnings("null")
        @Test
        @DisplayName("sets firmware version and records timestamp on active device")
        void updateFirmware_setsFirmwareVersionAndTimestamp() {
            Device d = deviceWithLifecycle(DeviceLifecycleStatus.ACTIVE, "ONLINE");
            when(deviceRepository.findById(deviceId)).thenReturn(Optional.of(d));
            when(deviceRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            FirmwareUpdateRequest req = new FirmwareUpdateRequest();
            req.setFirmwareVersion("2.3.1");

            Device result = deviceService.updateFirmware(deviceId, req);

            assertThat(result.getFirmwareVersion()).as("firmware version").isEqualTo("2.3.1");
            assertThat(result.getFirmwareUpdatedAt()).as("firmware timestamp").isNotNull();
        }

        @SuppressWarnings("null")
        @Test
        @DisplayName("throws on decommissioned device")
        void updateFirmware_throwsWhenDeviceIsDecommissioned() {
            Device d = deviceWithLifecycle(DeviceLifecycleStatus.DECOMMISSIONED, "OFFLINE");
            when(deviceRepository.findById(deviceId)).thenReturn(Optional.of(d));

            FirmwareUpdateRequest req = new FirmwareUpdateRequest();
            req.setFirmwareVersion("2.3.1");

            assertThatThrownBy(() -> deviceService.updateFirmware(deviceId, req))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("decommissioned");
        }
    }

    // ── updateCapabilities ────────────────────────────────────────────────────

    @Nested
    @DisplayName("updateCapabilities()")
    class UpdateCapabilities {

        @SuppressWarnings("null")
        @Test
        @DisplayName("replaces capability map on active device")
        void updateCapabilities_replacesCapabilityMap() {
            Device d = deviceWithLifecycle(DeviceLifecycleStatus.ACTIVE, "ONLINE");
            when(deviceRepository.findById(deviceId)).thenReturn(Optional.of(d));
            when(deviceRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            Map<String, SensorCapability> caps = Map.of(
                    "TEMPERATURE", SensorCapability.above("°C", 75.0, 90.0, 1));

            Device result = deviceService.updateCapabilities(deviceId,
                    new DeviceCapabilityRequest(caps));

            assertThat(result.getCapabilities()).containsKey("TEMPERATURE");
        }

        @SuppressWarnings("null")
        @Test
        @DisplayName("throws on decommissioned device")
        void updateCapabilities_throwsWhenDeviceIsDecommissioned() {
            Device d = deviceWithLifecycle(DeviceLifecycleStatus.DECOMMISSIONED, "OFFLINE");
            when(deviceRepository.findById(deviceId)).thenReturn(Optional.of(d));

            assertThatThrownBy(() -> deviceService.updateCapabilities(deviceId,
                    new DeviceCapabilityRequest(Map.of())))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("decommissioned");
        }
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private Device device(String name, String status) {
        Device d = new Device();
        d.setId(UUID.randomUUID());
        d.setName(name);
        d.setStatus(status);
        return d;
    }

    private Device deviceWithId(UUID id, String status) {
        Device d = new Device();
        d.setId(id);
        d.setStatus(status);
        return d;
    }

    private Device deviceWithLifecycle(DeviceLifecycleStatus lifecycle, String status) {
        Device d = deviceWithId(deviceId, status);
        d.setLifecycleStatus(lifecycle);
        return d;
    }

    private DeviceLifecycleRequest lifecycleRequest(DeviceLifecycleStatus status) {
        DeviceLifecycleRequest req = new DeviceLifecycleRequest();
        req.setLifecycleStatus(status);
        return req;
    }
}
