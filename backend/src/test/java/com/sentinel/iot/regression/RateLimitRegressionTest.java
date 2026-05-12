package com.sentinel.iot.regression;

import com.sentinel.iot.security.RateLimitFilter;
import org.junit.jupiter.api.BeforeEach;
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
class RateLimitRegressionTest {

    private RateLimitFilter filter;

    @BeforeEach
    void setUp() {
        filter = new RateLimitFilter();
        ReflectionTestUtils.setField(filter, "trustedProxiesConfig", "");
    }

    // 3.7.1 — Auth endpoint enforces 10 req/min; 11th request returns 429
    @Test
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

    // 3.7.2 — API endpoint enforces 100 req/min; 101st request returns 429
    @Test
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

    // 3.7.3 — Non-API paths (e.g. /actuator/health) are exempt from rate limiting
    @Test
    void actuatorPath_isExemptFromRateLimit() throws Exception {
        String ip = "10.7.1.3";
        for (int i = 0; i < 150; i++) {
            assertThat(invoke("/actuator/health", ip).getStatus())
                    .as("actuator request %d must not be rate-limited", i + 1)
                    .isNotEqualTo(429);
        }
    }

    // 3.7.4 — 429 response body is JSON {"error": "Rate limit exceeded..."}
    @Test
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

    // 3.7.5 — /devices/enroll uses the auth bucket (10/min), not the API bucket (100/min)
    @Test
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

    // ── helpers ───────────────────────────────────────────────────────────────

    private MockHttpServletResponse invoke(String path, String remoteAddr) throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest("GET", path);
        req.setRemoteAddr(remoteAddr);
        MockHttpServletResponse res = new MockHttpServletResponse();
        filter.doFilter(req, res, new MockFilterChain());
        return res;
    }
}
