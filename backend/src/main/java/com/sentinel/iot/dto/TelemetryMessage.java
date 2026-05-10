package com.sentinel.iot.dto;

import lombok.Data;

@Data
public class TelemetryMessage {
    private String deviceId;
    private Double temperature;
    private Double humidity;
    private Boolean motion;
    private Double smokePpm;
    private Long timestamp;
}
