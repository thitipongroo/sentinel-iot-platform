package com.sentinel.iot.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ReplayQueueMessage {
    private UUID deviceId;
    private Double temperature;
    private Double humidity;
    private Boolean motion;
    private Double smokePpm;
    private Instant timestamp;
}
