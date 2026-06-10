package com.sentinel.iot;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sentinel.iot.service.SchemaCompatibilityService;
import org.junit.jupiter.api.BeforeEach;
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
import static org.mockito.Mockito.*;

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

    // ── disabled (no-op) ──────────────────────────────────────────────────────

    @Test
    void run_disabled_isNoOp() throws Exception {
        // schema-registry.enabled defaults to false after construction — no resource loading
        service.run(appArgs);

        verifyNoInteractions(resourceLoader);
    }

    // ── enabled: fail-open on unreadable resource ─────────────────────────────

    @SuppressWarnings("null")
    @Test
    void run_enabled_resourceLoadFails_doesNotThrow() throws Exception {
        ReflectionTestUtils.setField(service, "enabled", true);
        when(resourceLoader.getResource(anyString())).thenReturn(resource);
        when(resource.getContentAsString(any(Charset.class)))
                .thenThrow(new IOException("classpath resource not found"));

        // Registry is unreachable / resource missing → warn and continue, never abort startup
        assertThatNoException().isThrownBy(() -> service.run(appArgs));
    }

    // ── disabled does not interact with ObjectMapper ──────────────────────────

    @SuppressWarnings("null")
    @Test
    void run_disabled_withExplicitFalse_isNoOp() throws Exception {
        ReflectionTestUtils.setField(service, "enabled", false);

        service.run(appArgs);

        verifyNoInteractions(resourceLoader);
    }
}
