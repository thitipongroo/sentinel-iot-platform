package com.sentinel.iot.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class DeviceRequest {
    @NotBlank(message = "Device name is required")
    private String name;
    private String description;
    private String location;
}
