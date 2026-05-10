package com.sentinel.iot.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "alerts", indexes = {
    @Index(name = "idx_alert_device_id", columnList = "device_id")
})
@Data
@NoArgsConstructor
public class Alert {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "device_id", nullable = false)
    private UUID deviceId;

    @Column(nullable = false)
    private String level; // CRITICAL, WARNING, INFO

    @Column(nullable = false, columnDefinition = "TEXT")
    private String message;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @Column(name = "acknowledged")
    private boolean acknowledged = false;

    public Alert(UUID deviceId, String level, String message) {
        this.deviceId = deviceId;
        this.level = level;
        this.message = message;
    }
}
