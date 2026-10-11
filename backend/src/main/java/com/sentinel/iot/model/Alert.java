package com.sentinel.iot.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "alerts", indexes = {
    @Index(name = "idx_alert_device_id", columnList = "device_id"),
    @Index(name = "idx_alert_org_id",    columnList = "organization_id")
})
@Data
@NoArgsConstructor
public class Alert {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "device_id", nullable = false)
    private UUID deviceId;

    @Column(name = "organization_id")
    private UUID organizationId;

    @Column(nullable = false)
    private String level; // CRITICAL, WARNING, INFO

    @Column(nullable = false, columnDefinition = "TEXT")
    private String message;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AlertState state = AlertState.FIRING;

    @Column(name = "acknowledged")
    private boolean acknowledged = false;

    @Column(name = "acknowledged_at")
    private Instant acknowledgedAt;

    @Column(name = "acknowledged_by", length = 100)
    private String acknowledgedBy;

    @Column(name = "resolved_at")
    private Instant resolvedAt;

    @Column(name = "resolved_by", length = 100)
    private String resolvedBy;

    @Column(name = "resolution_notes", columnDefinition = "TEXT")
    private String resolutionNotes;

    @Column(name = "silenced_until")
    private Instant silencedUntil;

    @Column(name = "silenced_by", length = 100)
    private String silencedBy;

    public Alert(UUID deviceId, String level, String message, UUID organizationId) {
        this.deviceId = deviceId;
        this.level = level;
        this.message = message;
        this.organizationId = organizationId;
        this.state = AlertState.FIRING;
        this.acknowledged = false;
    }

    public void transitionToAcknowledged(String operator) {
        this.state = AlertState.ACKNOWLEDGED;
        this.acknowledged = true;
        this.acknowledgedAt = Instant.now();
        this.acknowledgedBy = operator;
    }

    public void transitionToResolved(String operator, String notes) {
        this.state = AlertState.RESOLVED;
        this.acknowledged = true;
        this.resolvedAt = Instant.now();
        this.resolvedBy = operator;
        this.resolutionNotes = notes;
    }

    public void transitionToSilenced(String operator, Instant until) {
        this.state = AlertState.SILENCED;
        this.silencedUntil = until;
        this.silencedBy = operator;
    }

    public void transitionToFiring() {
        this.state = AlertState.FIRING;
        this.acknowledged = false;
    }
}
