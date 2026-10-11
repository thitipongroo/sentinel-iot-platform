package com.sentinel.iot.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Entity representing a Device Twin / Shadow document.
 * Maintains desired, reported, and metadata states with optimistic concurrency versioning.
 */
@Entity
@Table(name = "device_shadows")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DeviceShadow {

    @Id
    @Column(name = "device_id", nullable = false)
    private UUID deviceId;

    @Column(name = "organization_id", nullable = false)
    private UUID organizationId;

    @Column(nullable = false)
    @Builder.Default
    private Long version = 1L;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "desired_state", columnDefinition = "jsonb", nullable = false)
    @Builder.Default
    private Map<String, Object> desiredState = new HashMap<>();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "reported_state", columnDefinition = "jsonb", nullable = false)
    @Builder.Default
    private Map<String, Object> reportedState = new HashMap<>();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata", columnDefinition = "jsonb", nullable = false)
    @Builder.Default
    private Map<String, Object> metadata = new HashMap<>();

    @Column(name = "updated_at", nullable = false)
    @Builder.Default
    private Instant updatedAt = Instant.now();

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;
}
