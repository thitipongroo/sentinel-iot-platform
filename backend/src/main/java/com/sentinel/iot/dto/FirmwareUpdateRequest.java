package com.sentinel.iot.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class FirmwareUpdateRequest {

    @NotBlank(message = "firmwareVersion is required")
    @Pattern(regexp = "^\\d+\\.\\d+\\.\\d+(-[\\w.]+)?$",
             message = "firmwareVersion must follow semver (e.g. 1.2.3 or 1.2.3-beta.1)")
    private String firmwareVersion;
}
