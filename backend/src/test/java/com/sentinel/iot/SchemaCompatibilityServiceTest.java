package com.sentinel.iot;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sentinel.iot.service.SchemaCompatibilityService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.ApplicationArguments;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.nio.charset.Charset;

import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@Tag("unit")
@DisplayName("SchemaCompatibilityService")
@ExtendWith(MockitoExtension.class)
class SchemaCompatibilityServiceTest {

    @Mock ResourceLoader      resourceLoader;
    @Mock ApplicationArguments appArgs;
    @Mock Resource             resource;

    SchemaCompatibilityService service;

    @BeforeEach
    void setUp() {
        service = new SchemaCompatibilityService(resourceLoader, new ObjectMapper());
    }

    // ── Disabled mode (default) ───────────────────────────────────────────────

    @Nested
    @DisplayName("Disabled mode (schema-registry.enabled = false)")
    class DisabledMode {

        @Test
        @DisplayName("run() is a no-op and never touches the ResourceLoader when disabled by default")
        void run_disabled_isNoOp() throws Exception {
            service.run(appArgs);

            verifyNoInteractions(resourceLoader);
        }

        @SuppressWarnings("null")
        @Test
        @DisplayName("run() is a no-op even when enabled is explicitly set to false")
        void run_disabled_withExplicitFalse_isNoOp() throws Exception {
            ReflectionTestUtils.setField(service, "enabled", false);

            service.run(appArgs);

            verifyNoInteractions(resourceLoader);
        }
    }

    // ── Enabled mode ──────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Enabled mode (schema-registry.enabled = true)")
    class EnabledMode {

        @SuppressWarnings("null")
        @Test
        @DisplayName("fails open and does not throw when the schema resource cannot be read at startup")
        void run_enabled_resourceLoadFails_doesNotThrow() throws Exception {
            ReflectionTestUtils.setField(service, "enabled", true);
            when(resourceLoader.getResource(anyString())).thenReturn(resource);
            when(resource.getContentAsString(any(Charset.class)))
                    .thenThrow(new IOException("classpath resource not found"));

            // Registry unreachable / resource missing → warn and continue, never abort startup
            assertThatNoException()
                    .as("schema compatibility check must be fail-open at startup")
                    .isThrownBy(() -> service.run(appArgs));
        }
    }
}
