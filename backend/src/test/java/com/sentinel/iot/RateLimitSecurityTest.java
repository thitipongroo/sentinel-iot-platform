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

/**
 * หมวดที่ 5 — Rate Limiting Security (5 tests)
 *
 * ทดสอบ: auth endpoint 10/min limit, API endpoint 100/min limit,
 *        X-Forwarded-For spoofing prevention, per-IP bucket isolation,
 *        and bucket reset after the refill window elapses.
 *
 * Uses a fresh RateLimitFilter per test to avoid shared bucket state.
 * No trusted proxies configured (empty string) — mirrors production default.
 */
@Tag("unit")
@DisplayName("RateLimitFilter security — rate limiting invariants")
class RateLimitSecurityTest {

    private RateLimitFilter filter;

    @BeforeEach
    void setUp() {
        filter = new RateLimitFilter();
        // No trusted proxies: X-Forwarded-For header is always ignored (spoofing prevention)
        ReflectionTestUtils.setField(filter, "trustedProxiesConfig", "");
    }

    // ── Auth endpoint limits ──────────────────────────────────────────────────

    @Nested
    @DisplayName("Auth endpoint limits (10 req/min)")
    class AuthEndpointLimits {

        @Test
        @DisplayName("first 10 requests to /api/v1/auth/login are allowed; the 11th is rejected with 429")
        void authEndpoint_isLimitedAt10RequestsPerMinute() throws Exception {
            String ip = "192.168.5.1";
            for (int i = 0; i < 10; i++) {
                assertThat(invoke("/api/v1/auth/login", ip).getStatus())
                        .as("request %d should pass", i + 1)
                        .isNotEqualTo(429);
            }
            assertThat(invoke("/api/v1/auth/login", ip).getStatus())
                    .as("11th auth request should be rate limited").isEqualTo(429);
        }
    }

    // ── API endpoint limits ───────────────────────────────────────────────────

    @Nested
    @DisplayName("API endpoint limits (100 req/min)")
    class ApiEndpointLimits {

        @Test
        @DisplayName("first 100 requests to /api/v1/devices are allowed; the 101st is rejected with 429")
        void apiEndpoint_isLimitedAt100RequestsPerMinute() throws Exception {
            String ip = "192.168.5.2";
            for (int i = 0; i < 100; i++) {
                assertThat(invoke("/api/v1/devices", ip).getStatus())
                        .as("request %d should pass", i + 1)
                        .isNotEqualTo(429);
            }
            assertThat(invoke("/api/v1/devices", ip).getStatus())
                    .as("101st API request should be rate limited").isEqualTo(429);
        }
    }

    // ── Spoofing prevention ───────────────────────────────────────────────────

    @Nested
    @DisplayName("X-Forwarded-For spoofing prevention")
    class SpoofingPrevention {

        @SuppressWarnings("null")
        @Test
        @DisplayName("X-Forwarded-For is ignored when no trusted proxy is configured — real IP is used")
        void xForwardedFor_withoutTrustedProxy_doesNotBypassRateLimit() throws Exception {
            String realIp    = "192.168.5.3";
            String spoofedIp = "1.2.3.4";

            // Exhaust the real IP's bucket
            for (int i = 0; i < 100; i++) {
                invoke("/api/v1/devices", realIp);
            }

            // Attacker adds X-Forwarded-For to appear as a fresh IP — must be ignored
            MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/v1/devices");
            req.setRemoteAddr(realIp);
            req.addHeader("X-Forwarded-For", spoofedIp);
            MockHttpServletResponse res = new MockHttpServletResponse();
            filter.doFilter(req, res, new MockFilterChain());

            assertThat(res.getStatus())
                    .as("spoofed XFF without trusted proxy must use the real (exhausted) IP bucket")
                    .isEqualTo(429);
        }
    }

    // ── Per-client bucket isolation ───────────────────────────────────────────

    @Nested
    @DisplayName("Per-client bucket isolation")
    class BucketIsolation {

        @Test
        @DisplayName("different source IPs have independent rate-limit buckets")
        void differentIps_haveIndependentBuckets() throws Exception {
            String ipA = "192.168.5.10";
            String ipB = "192.168.5.11";

            // Exhaust IP A's auth bucket (10/min)
            for (int i = 0; i < 10; i++) {
                invoke("/api/v1/auth/login", ipA);
            }
            assertThat(invoke("/api/v1/auth/login", ipA).getStatus())
                    .as("IP A should be blocked after 10 auth requests").isEqualTo(429);

            // IP B's bucket is independent — first request must pass
            assertThat(invoke("/api/v1/auth/login", ipB).getStatus())
                    .as("IP B must not be affected by IP A's exhausted bucket").isNotEqualTo(429);
        }

        @Test
        @DisplayName("evicting a bucket simulates a window reset — next request is accepted")
        void afterWindowReset_newRequestsAreAccepted() throws Exception {
            String ip = "192.168.5.20";
            for (int i = 0; i < 10; i++) {
                invoke("/api/v1/auth/login", ip);
            }
            assertThat(invoke("/api/v1/auth/login", ip).getStatus())
                    .as("11th request should be blocked").isEqualTo(429);

            // Evict the bucket — equivalent to the greedy-refill window resetting in production
            @SuppressWarnings("unchecked")
            java.util.Map<String, Object> buckets =
                    (java.util.Map<String, Object>) ReflectionTestUtils.getField(filter, "buckets");
            assertThat(buckets).isNotNull();
            buckets.remove("auth:" + ip);

            assertThat(invoke("/api/v1/auth/login", ip).getStatus())
                    .as("first request after window reset should be accepted").isNotEqualTo(429);
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    @SuppressWarnings("null")
    private MockHttpServletResponse invoke(String path, String remoteAddr) throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest("POST", path);
        req.setRemoteAddr(remoteAddr);
        MockHttpServletResponse res = new MockHttpServletResponse();
        filter.doFilter(req, res, new MockFilterChain());
        return res;
    }
}
