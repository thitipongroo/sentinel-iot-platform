package com.sentinel.iot;

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

@Tag("unit")
@DisplayName("RateLimitFilter")
class RateLimitFilterTest {

    RateLimitFilter filter;

    @BeforeEach
    void setUp() {
        filter = new RateLimitFilter();
        ReflectionTestUtils.setField(filter, "trustedProxiesConfig", "10.0.0.1");
    }

    // ── Allowance window ──────────────────────────────────────────────────────

    @Nested
    @DisplayName("Within-limit behaviour")
    class WithinLimit {

        @Test
        @DisplayName("first 100 requests from a single IP are all allowed")
        void first100ApiRequests_areAllAllowed() throws Exception {
            for (int i = 0; i < 100; i++) {
                assertThat(invoke("/api/devices", "10.1.1.1").getStatus())
                        .as("request %d should pass", i + 1)
                        .isNotEqualTo(429);
            }
        }

        @Test
        @DisplayName("non-API paths (e.g. /actuator) are never rate-limited")
        void nonApiPath_isNeverRateLimited() throws Exception {
            for (int i = 0; i < 200; i++) {
                assertThat(invoke("/actuator/health", "10.4.4.4").getStatus())
                        .as("non-api request %d should not be rate limited", i + 1)
                        .isNotEqualTo(429);
            }
        }
    }

    // ── Rate-limited responses ────────────────────────────────────────────────

    @Nested
    @DisplayName("Over-limit behaviour")
    class OverLimit {

        @Test
        @DisplayName("request 101 from same IP returns HTTP 429")
        void request101_isRateLimited() throws Exception {
            for (int i = 0; i < 100; i++) {
                invoke("/api/devices", "10.2.2.2");
            }
            assertThat(invoke("/api/devices", "10.2.2.2").getStatus()).isEqualTo(429);
        }

        @Test
        @DisplayName("rate-limited response body contains 'Rate limit exceeded'")
        void rateLimitedResponse_containsErrorJson() throws Exception {
            for (int i = 0; i < 100; i++) {
                invoke("/api/telemetry/stats", "10.3.3.3");
            }
            MockHttpServletResponse response = invoke("/api/telemetry/stats", "10.3.3.3");

            assertThat(response.getStatus()).isEqualTo(429);
            assertThat(response.getContentAsString()).contains("Rate limit exceeded");
        }
    }

    // ── Per-client bucket isolation ───────────────────────────────────────────

    @Nested
    @DisplayName("Per-client bucket isolation")
    class BucketIsolation {

        @Test
        @DisplayName("different client IPs have independent rate-limit buckets")
        void differentClientIPs_haveIndependentBuckets() throws Exception {
            for (int i = 0; i < 100; i++) {
                invoke("/api/devices", "192.168.1.10");
            }
            assertThat(invoke("/api/devices", "192.168.1.20").getStatus())
                    .as("IP 192.168.1.20 must not be affected by 192.168.1.10's exhausted bucket")
                    .isNotEqualTo(429);
        }

        @Test
        @DisplayName("X-Forwarded-For is used as the client IP for bucket keying")
        void xForwardedForHeader_isUsedAsClientIp() throws Exception {
            for (int i = 0; i < 100; i++) {
                invokeForwarded("/api/devices", "203.0.113.5");
            }
            assertThat(invokeForwarded("/api/devices", "203.0.113.5").getStatus())
                    .as("forwarded IP 203.0.113.5 should be limited after 100 requests")
                    .isEqualTo(429);
            assertThat(invokeForwarded("/api/devices", "203.0.113.6").getStatus())
                    .as("different forwarded IP must not be affected")
                    .isNotEqualTo(429);
        }
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    @SuppressWarnings("null")
    private MockHttpServletResponse invoke(String path, String remoteAddr) throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest("GET", path);
        req.setRemoteAddr(remoteAddr);
        MockHttpServletResponse res = new MockHttpServletResponse();
        filter.doFilter(req, res, new MockFilterChain());
        return res;
    }

    @SuppressWarnings("null")
    private MockHttpServletResponse invokeForwarded(String path, String forwardedIp) throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest("GET", path);
        req.setRemoteAddr("10.0.0.1");
        req.addHeader("X-Forwarded-For", forwardedIp);
        MockHttpServletResponse res = new MockHttpServletResponse();
        filter.doFilter(req, res, new MockFilterChain());
        return res;
    }
}
