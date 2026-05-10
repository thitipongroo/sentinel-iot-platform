package com.sentinel.iot.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "audit_logs", indexes = {
    @Index(name = "idx_audit_log_username",  columnList = "username"),
    @Index(name = "idx_audit_log_timestamp", columnList = "timestamp")
})
@Data
@NoArgsConstructor
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column
    private String username;

    @Column(nullable = false, length = 100)
    private String action;

    @Column
    private String resource;

    @Column(length = 1000)
    private String detail;

    @Column(name = "ip_address", length = 50)
    private String ipAddress;

    @Column(nullable = false)
    private Instant timestamp;

    public AuditLog(String username, String action, String resource, String detail, String ipAddress) {
        this.username = username;
        this.action = action;
        this.resource = resource;
        this.detail = detail;
        this.ipAddress = ipAddress;
        this.timestamp = Instant.now();
    }
}
