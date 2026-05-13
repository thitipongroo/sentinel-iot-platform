package com.sentinel.iot;

import com.sentinel.iot.security.RateLimitFilter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * หมวดที่ 5 — Rate Limiting Security (4 tests)
 *
 * ทดสอบ: auth endpoint 10/min limit, API endpoint 100/min limit,
 *        X-Forwarded-For spoofing prevention, per-IP bucket isolation.
 *
 * Uses a fresh RateLimitFilter per test to avoid shared bucket state.
 * No trusted proxies configured (empty string) — mirrors production default.
 */
class RateLimitSecurityTest {

    private RateLimitFilter filter;

    @BeforeEach
    void setUp() {
        filter = new RateLimitFilter();
        // No trusted proxies: X-Forwarded-For header is always ignored (spoofing prevention)
        ReflectionTestUtils.setField(filter, "trustedProxiesConfig", "");
    }

    // ── 5.1 Auth endpoint limited to 10 req/min ──────────────────────────────

    @Test
    void authEndpoint_isLimitedAt10RequestsPerMinute() throws Exception {
        String ip = "192.168.5.1";
        for (int i = 0; i < 10; i++) {
            assertThat(invoke("/api/v1/auth/login", ip).getStatus())
                    .as("request %d should pass", i + 1)
                    .isNotEqualTo(429);
        }
        assertThat(invoke("/api/v1/auth/login", ip).getStatus())
                .as("11th auth request should be rate limited")
                .isEqualTo(429);
    }

    // ── 5.2 API endpoint limited to 100 req/min ──────────────────────────────

    @Test
    void apiEndpoint_isLimitedAt100RequestsPerMinute() throws Exception {
        String ip = "192.168.5.2";
        for (int i = 0; i < 100; i++) {
            assertThat(invoke("/api/v1/devices", ip).getStatus())
                    .as("request %d should pass", i + 1)
                    .isNotEqualTo(429);
        }
        assertThat(invoke("/api/v1/devices", ip).getStatus())
                .as("101st API request should be rate limited")
                .isEqualTo(429);
    }

    // ── 5.3 X-Forwarded-For without trusted proxy config is ignored ───────────

    @Test
    void xForwardedFor_withoutTrustedProxy_doesNotBypassRateLimit() throws Exception {
        String realIp   = "192.168.5.3";
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
                .as("Spoofed XFF without trusted proxy should still use real IP (exhausted)")
                .isEqualTo(429);
    }

    // ── 5.4 Different client IPs have independent rate limit buckets ──────────

    @Test
    void differentIps_haveIndependentBuckets() throws Exception {
        String ipA = "192.168.5.10";
        String ipB = "192.168.5.11";

        // Exhaust IP A's auth bucket (10/min)
        for (int i = 0; i < 10; i++) {
            invoke("/api/v1/auth/login", ipA);
        }
        assertThat(invoke("/api/v1/auth/login", ipA).getStatus())
                .as("IP A should be blocked after 10 auth requests")
                .isEqualTo(429);

        // IP B's bucket is independent — first request must pass
        assertThat(invoke("/api/v1/auth/login", ipB).getStatus())
                .as("IP B should not be affected by IP A's exhausted bucket")
                .isNotEqualTo(429);
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    @SuppressWarnings("null")
    private MockHttpServletResponse invoke(String path, String remoteAddr) throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest("POST", path);
        req.setRemoteAddr(remoteAddr);
        MockHttpServletResponse res = new MockHttpServletResponse();
        filter.doFilter(req, res, new MockFilterChain());
        return res;
    }
}
