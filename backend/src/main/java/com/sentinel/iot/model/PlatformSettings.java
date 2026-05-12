package com.sentinel.iot.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "platform_settings")
@Data
@NoArgsConstructor
public class PlatformSettings {

    @Id
    private UUID organizationId;

    private double  temperatureThreshold;
    private double  humidityThreshold;
    private double  smokeThreshold;
    private int     telemetryRetentionDays;
    private int     auditRetentionDays;
    private boolean slackEnabled;
    private boolean lineEnabled;
    private boolean webhookEnabled;
    private Instant updatedAt;
    private String  updatedBy;
}
