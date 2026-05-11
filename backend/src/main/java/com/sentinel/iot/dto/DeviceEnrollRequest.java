package com.sentinel.iot.dto;

import java.util.UUID;

public record DeviceEnrollRequest(
        UUID   deviceId,
        String token,
        String publicKey  // optional — present if the device supports mTLS or key-based auth
) {}
