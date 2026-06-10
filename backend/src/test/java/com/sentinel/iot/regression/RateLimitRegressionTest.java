package com.sentinel.iot.regression;

import com.sentinel.iot.security.RateLimitFilter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 3.7 Rate Limiting Configuration Regression (5 tests)
 *
 * Uses a fresh RateLimitFilter per test to avoid shared bucket state.
 */
@Tag("unit")
@DisplayName("RateLimitRegressionTest — rate limit configuration invariants")
class RateLimitRegressionTest {

    private RateLimitFilter filter;

    @BeforeEach
    void setUp() {
        filter = new RateLimitFilter();
        ReflectionTestUtils.setField(filter, "trustedProxiesConfig", "");
    }

    // ── Auth bucket ───────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Auth bucket (10 req/min)")
    class AuthBucket {

        @Test
        @DisplayName("auth endpoint: first 10 requests pass, 11th returns 429")
        void authEndpoint_11thRequest_returns429() throws Exception {
            String ip = "10.7.1.1";
            for (int i = 0; i < 10; i++) {
                assertThat(invoke("/api/v1/auth/login", ip).getStatus())
                        .as("request %d should pass", i + 1)
                        .isNotEqualTo(429);
            }
            assertThat(invoke("/api/v1/auth/login", ip).getStatus())
                    .as("11th auth request must be rate-limited")
                    .isEqualTo(429);
        }

        @Test
        @DisplayName("/devices/enroll uses the auth bucket — 11th request returns 429")
        void devicesEnroll_usesAuthBucket_limitAt10() throws Exception {
            String ip = "10.7.1.5";
            for (int i = 0; i < 10; i++) {
                assertThat(invoke("/api/v1/devices/enroll", ip).getStatus())
                        .as("enroll request %d should pass", i + 1)
                        .isNotEqualTo(429);
            }
            assertThat(invoke("/api/v1/devices/enroll", ip).getStatus())
                    .as("11th enroll request must hit the auth bucket limit")
                    .isEqualTo(429);
        }
    }

    // ── API bucket ────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("API bucket (100 req/min)")
    class ApiBucket {

        @Test
        @DisplayName("API endpoint: first 100 requests pass, 101st returns 429")
        void apiEndpoint_101stRequest_returns429() throws Exception {
            String ip = "10.7.1.2";
            for (int i = 0; i < 100; i++) {
                assertThat(invoke("/api/v1/devices", ip).getStatus())
                        .as("request %d should pass", i + 1)
                        .isNotEqualTo(429);
            }
            assertThat(invoke("/api/v1/devices", ip).getStatus())
                    .as("101st API request must be rate-limited")
                    .isEqualTo(429);
        }
    }

    // ── Exempt paths ──────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Exempt paths")
    class ExemptPaths {

        @Test
        @DisplayName("/actuator/health is exempt from rate limiting — 150 requests all pass")
        void actuatorPath_isExemptFromRateLimit() throws Exception {
            String ip = "10.7.1.3";
            for (int i = 0; i < 150; i++) {
                assertThat(invoke("/actuator/health", ip).getStatus())
                        .as("actuator request %d must not be rate-limited", i + 1)
                        .isNotEqualTo(429);
            }
        }
    }

    // ── Response format ───────────────────────────────────────────────────────

    @Nested
    @DisplayName("429 response format")
    class ResponseFormat {

        @Test
        @DisplayName("429 response body is JSON with an 'error' field containing 'Rate limit exceeded'")
        void rateLimitResponse_bodyIsJson_withErrorField() throws Exception {
            String ip = "10.7.1.4";
            for (int i = 0; i < 10; i++) {
                invoke("/api/v1/auth/login", ip);
            }
            MockHttpServletResponse res = invoke("/api/v1/auth/login", ip);

            assertThat(res.getStatus()).isEqualTo(429);
            assertThat(res.getContentType()).contains("application/json");
            assertThat(res.getContentAsString()).contains("\"error\"");
            assertThat(res.getContentAsString()).contains("Rate limit exceeded");
        }
    }

    // ── helper ────────────────────────────────────────────────────────────────

    @SuppressWarnings("null")
    private MockHttpServletResponse invoke(String path, String remoteAddr) throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest("GET", path);
        req.setRemoteAddr(remoteAddr);
        MockHttpServletResponse res = new MockHttpServletResponse();
        filter.doFilter(req, res, new MockFilterChain());
        return res;
    }
}
