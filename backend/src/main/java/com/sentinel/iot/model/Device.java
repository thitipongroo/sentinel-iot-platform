package com.sentinel.iot.model;

import com.sentinel.iot.converter.DeviceCapabilitiesConverter;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static com.sentinel.iot.model.DeviceLifecycleStatus.PROVISIONED;

@Entity
@Table(name = "devices", uniqueConstraints = {
    @UniqueConstraint(name = "uq_device_org_name", columnNames = {"organization_id", "name"})
})
@Data
@NoArgsConstructor
public class Device {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @NotBlank
    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String status = "OFFLINE";

    @Column
    private String description;

    @Column
    private String location;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @Column(name = "last_seen")
    private Instant lastSeen;

    @Enumerated(EnumType.STRING)
    @Column(name = "lifecycle_status", nullable = false)
    private DeviceLifecycleStatus lifecycleStatus = PROVISIONED;

    @Column(name = "firmware_version")
    private String firmwareVersion;

    @Column(name = "firmware_updated_at")
    private Instant firmwareUpdatedAt;

    @Column(name = "organization_id", nullable = false)
    private UUID organizationId;

    /**
     * Sensor capability declarations for this device, keyed by {@link SensorType#name()}.
     *
     * <p>When populated, the alert engine uses per-device thresholds from each
     * {@link SensorCapability} instead of the global application.yml values.
     * Null/empty means the device has not declared capabilities; global thresholds apply.</p>
     *
     * Example:
     * {"TEMPERATURE":{"unit":"°C","warnThreshold":75.0,"critThreshold":90.0,...}, ...}
     */
    @Convert(converter = DeviceCapabilitiesConverter.class)
    @Column(name = "capabilities", columnDefinition = "jsonb")
    private Map<String, SensorCapability> capabilities;
}
