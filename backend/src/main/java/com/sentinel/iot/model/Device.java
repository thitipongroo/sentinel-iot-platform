package com.sentinel.iot.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

import static com.sentinel.iot.model.DeviceLifecycleStatus.PROVISIONED;

@Entity
@Table(name = "devices")
@Data
@NoArgsConstructor
public class Device {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @NotBlank
    @Column(nullable = false, unique = true)
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
}
