package com.sentinel.iot.dto;

import com.sentinel.iot.model.DeviceLifecycleStatus;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class DeviceLifecycleRequest {

    @NotNull(message = "lifecycleStatus is required")
    private DeviceLifecycleStatus lifecycleStatus;
}
