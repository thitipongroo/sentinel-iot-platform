package com.sentinel.iot;

import com.sentinel.iot.config.RequestIdFilter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;

@Tag("unit")
@DisplayName("RequestIdFilter")
class RequestIdFilterTest {

    private RequestIdFilter filter;

    @BeforeEach
    void setUp() {
        MDC.clear();
        filter = new RequestIdFilter();
    }

    // ── Request-ID propagation ────────────────────────────────────────────────

    @Nested
    @DisplayName("Request-ID propagation")
    class RequestIdPropagation {

        @Test
        @DisplayName("echoes a client-supplied X-Request-ID back in the response")
        void providedRequestId_isEchoedInResponse() throws Exception {
            MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/devices");
            request.addHeader("X-Request-ID", "client-req-abc-123");
            MockHttpServletResponse response = new MockHttpServletResponse();

            filter.doFilter(request, response, new MockFilterChain());

            assertThat(response.getHeader("X-Request-ID")).isEqualTo("client-req-abc-123");
        }

        @Test
        @DisplayName("generates a UUID when no X-Request-ID header is provided")
        void missingRequestId_generatesUuidAndSetsInResponse() throws Exception {
            MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/devices");
            MockHttpServletResponse response = new MockHttpServletResponse();

            filter.doFilter(request, response, new MockFilterChain());

            String requestId = response.getHeader("X-Request-ID");
            assertThat(requestId).isNotNull().isNotBlank();
            assertThatNoException()
                    .as("generated ID must be a valid UUID")
                    .isThrownBy(() -> UUID.fromString(requestId));
        }

        @Test
        @DisplayName("generates a new UUID when the supplied X-Request-ID is blank")
        void blankRequestId_generatesNewUuid() throws Exception {
            MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/devices");
            request.addHeader("X-Request-ID", "   ");
            MockHttpServletResponse response = new MockHttpServletResponse();

            filter.doFilter(request, response, new MockFilterChain());

            String requestId = response.getHeader("X-Request-ID");
            assertThat(requestId).isNotBlank().isNotEqualTo("   ");
        }
    }

    // ── MDC lifecycle ─────────────────────────────────────────────────────────

    @Nested
    @DisplayName("MDC lifecycle")
    class MdcLifecycle {

        @Test
        @DisplayName("populates requestId, method, and path in MDC during filter execution")
        void mdcIsPopulated_duringFilterExecution() throws Exception {
            MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/telemetry");
            request.addHeader("X-Request-ID", "mdc-test-id");
            MockHttpServletResponse response = new MockHttpServletResponse();

            AtomicReference<String> capturedRequestId = new AtomicReference<>();
            AtomicReference<String> capturedMethod    = new AtomicReference<>();
            AtomicReference<String> capturedPath      = new AtomicReference<>();

            filter.doFilter(request, response, (req, res) -> {
                capturedRequestId.set(MDC.get("requestId"));
                capturedMethod.set(MDC.get("method"));
                capturedPath.set(MDC.get("path"));
            });

            assertThat(capturedRequestId.get()).as("requestId in MDC").isEqualTo("mdc-test-id");
            assertThat(capturedMethod.get()).as("method in MDC").isEqualTo("POST");
            assertThat(capturedPath.get()).as("path in MDC").isEqualTo("/api/v1/telemetry");
        }

        @Test
        @DisplayName("clears all MDC keys after the filter chain completes")
        void mdcIsCleared_afterFilterCompletes() throws Exception {
            MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/devices");
            request.addHeader("X-Request-ID", "clear-test-id");

            filter.doFilter(request, new MockHttpServletResponse(), new MockFilterChain());

            assertThat(MDC.get("requestId")).as("requestId cleared").isNull();
            assertThat(MDC.get("method")).as("method cleared").isNull();
            assertThat(MDC.get("path")).as("path cleared").isNull();
        }

        @Test
        @DisplayName("clears MDC even when the filter chain throws")
        void mdcIsCleared_evenWhenFilterChainThrows() throws Exception {
            MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/devices");
            MockHttpServletResponse response = new MockHttpServletResponse();

            try {
                filter.doFilter(request, response,
                        (req, res) -> { throw new RuntimeException("Simulated error"); });
            } catch (RuntimeException ignored) {
                // expected
            }

            assertThat(MDC.get("requestId"))
                    .as("MDC must be clean even after exception")
                    .isNull();
        }
    }
}
