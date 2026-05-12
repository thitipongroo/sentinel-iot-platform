package com.sentinel.iot.dto;

import jakarta.validation.constraints.*;

public record UpdateSettingsRequest(
        @NotNull @DecimalMin("0") @DecimalMax("200") Double temperatureCelsius,
        @NotNull @DecimalMin("0") @DecimalMax("100") Double humidityPercent,
        @NotNull @DecimalMin("0")                    Double smokePpm,
        @NotNull @Min(1) @Max(3650)                  Integer telemetryDays,
        @NotNull @Min(1) @Max(3650)                  Integer auditDays,
        Boolean slack,
        Boolean line,
        Boolean webhook
) {}
