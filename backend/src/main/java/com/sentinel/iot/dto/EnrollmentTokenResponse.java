package com.sentinel.iot.dto;

import java.time.Instant;
import java.util.UUID;

public record EnrollmentTokenResponse(
        UUID   tokenId,
        String token,       // raw token — shown ONCE; the DB stores only a hash
        UUID   deviceId,
        Instant expiresAt
) {}
