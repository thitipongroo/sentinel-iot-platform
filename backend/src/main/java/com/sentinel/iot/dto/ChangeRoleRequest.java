package com.sentinel.iot.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record ChangeRoleRequest(
        @NotBlank @Pattern(regexp = "ADMIN|OPERATOR", message = "role must be ADMIN or OPERATOR")
        String role
) {}
