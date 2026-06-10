package com.sentinel.iot;

import com.sentinel.iot.config.RequestIdFilter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;

class RequestIdFilterTest {

    private RequestIdFilter filter;

    @BeforeEach
    void setUp() {
        MDC.clear();
        filter = new RequestIdFilter();
    }

    @Test
    void providedRequestId_isEchoedInResponse() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/devices");
        request.addHeader("X-Request-ID", "client-req-abc-123");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertThat(response.getHeader("X-Request-ID")).isEqualTo("client-req-abc-123");
    }

    @Test
    void missingRequestId_generatesUuidAndSetsInResponse() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/devices");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        String requestId = response.getHeader("X-Request-ID");
        assertThat(requestId).isNotNull().isNotBlank();
        assertThatNoException().isThrownBy(() -> java.util.UUID.fromString(requestId));
    }

    @Test
    void blankRequestId_generatesNewUuid() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/devices");
        request.addHeader("X-Request-ID", "   ");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        String requestId = response.getHeader("X-Request-ID");
        assertThat(requestId).isNotBlank();
        assertThat(requestId).isNotEqualTo("   ");
    }

    @Test
    void mdcIsPopulated_duringFilterExecution() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/telemetry");
        request.addHeader("X-Request-ID", "mdc-test-id");
        MockHttpServletResponse response = new MockHttpServletResponse();

        AtomicReference<String> capturedRequestId = new AtomicReference<>();
        AtomicReference<String> capturedMethod = new AtomicReference<>();
        AtomicReference<String> capturedPath = new AtomicReference<>();

        filter.doFilter(request, response, (req, res) -> {
            capturedRequestId.set(MDC.get("requestId"));
            capturedMethod.set(MDC.get("method"));
            capturedPath.set(MDC.get("path"));
        });

        assertThat(capturedRequestId.get()).isEqualTo("mdc-test-id");
        assertThat(capturedMethod.get()).isEqualTo("POST");
        assertThat(capturedPath.get()).isEqualTo("/api/v1/telemetry");
    }

    @Test
    void mdcIsCleared_afterFilterCompletes() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/devices");
        request.addHeader("X-Request-ID", "clear-test-id");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertThat(MDC.get("requestId")).isNull();
        assertThat(MDC.get("method")).isNull();
        assertThat(MDC.get("path")).isNull();
    }

    @Test
    void mdcIsCleared_evenWhenFilterChainThrows() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/devices");
        MockHttpServletResponse response = new MockHttpServletResponse();

        try {
            filter.doFilter(request, response, (req, res) -> {
                throw new RuntimeException("Simulated filter chain error");
            });
        } catch (RuntimeException ignored) {
            // expected
        }

        assertThat(MDC.get("requestId")).isNull();
    }
}
