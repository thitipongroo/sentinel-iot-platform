package com.sentinel.iot.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreateUserRequest(
        @NotBlank @Size(min = 3, max = 50)
        String username,

        @NotBlank @Size(min = 8, max = 100)
        String password,

        @NotBlank @Pattern(regexp = "ADMIN|OPERATOR", message = "role must be ADMIN or OPERATOR")
        String role
) {}
