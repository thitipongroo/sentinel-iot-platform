package com.sentinel.iot.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class OpenApiConfig {

    private static final String BEARER_SCHEME = "bearerAuth";

    @Bean
    public OpenAPI sentinelOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Sentinel IoT Platform API")
                        .version("1.0.0")
                        .description("""
                                Real-time industrial IoT monitoring — devices, telemetry, and alerts.

                                **Versioning policy:** All production endpoints are prefixed with `/api/v1/`.
                                Breaking changes (field removal, type changes, renamed resources) will be
                                released under `/api/v2/` with a minimum 6-month deprecation window on v1.
                                Non-breaking additions (new optional fields, new endpoints) may appear in
                                the current version without a version bump.
                                """)
                        .contact(new Contact()
                                .name("Sentinel Platform Team")
                                .email("platform@sentinel-iot.io")))
                .servers(List.of(
                        new Server().url("/api/v1").description("Current stable version (v1)")
                ))
                .addSecurityItem(new SecurityRequirement().addList(BEARER_SCHEME))
                .components(new Components()
                        .addSecuritySchemes(BEARER_SCHEME, new SecurityScheme()
                                .name(BEARER_SCHEME)
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                                .description("Enter the JWT access token obtained from POST /api/v1/auth/login")));
    }
}
