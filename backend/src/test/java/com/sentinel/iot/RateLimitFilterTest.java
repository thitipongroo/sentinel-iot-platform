package com.sentinel.iot;

import com.sentinel.iot.security.RateLimitFilter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;

class RateLimitFilterTest {

    RateLimitFilter filter;

    @BeforeEach
    void setUp() {
        filter = new RateLimitFilter();
        // Allow the test's proxy IP so X-Forwarded-For is trusted in forwarded tests
        ReflectionTestUtils.setField(filter, "trustedProxiesConfig", "10.0.0.1");
    }

    @Test
    void first100ApiRequests_areAllowed() throws Exception {
        for (int i = 0; i < 100; i++) {
            assertThat(invoke("/api/devices", "10.1.1.1").getStatus())
                    .as("request %d should pass", i + 1)
                    .isNotEqualTo(429);
        }
    }

    @Test
    void request101_isRateLimited() throws Exception {
        for (int i = 0; i < 100; i++) {
            invoke("/api/devices", "10.2.2.2");
        }
        assertThat(invoke("/api/devices", "10.2.2.2").getStatus()).isEqualTo(429);
    }

    @Test
    void rateLimitedResponse_containsErrorJson() throws Exception {
        for (int i = 0; i < 100; i++) {
            invoke("/api/telemetry/stats", "10.3.3.3");
        }
        MockHttpServletResponse response = invoke("/api/telemetry/stats", "10.3.3.3");
        assertThat(response.getStatus()).isEqualTo(429);
        assertThat(response.getContentAsString()).contains("Rate limit exceeded");
    }

    @Test
    void nonApiPath_isNeverRateLimited() throws Exception {
        for (int i = 0; i < 200; i++) {
            assertThat(invoke("/actuator/health", "10.4.4.4").getStatus())
                    .as("non-api request %d should not be rate limited", i + 1)
                    .isNotEqualTo(429);
        }
    }

    @Test
    void differentClientIPs_haveIndependentBuckets() throws Exception {
        // exhaust bucket for IP A
        for (int i = 0; i < 100; i++) {
            invoke("/api/devices", "192.168.1.10");
        }
        // IP B should be unaffected
        assertThat(invoke("/api/devices", "192.168.1.20").getStatus()).isNotEqualTo(429);
    }

    @Test
    void xForwardedForHeader_isUsedAsClientIp() throws Exception {
        for (int i = 0; i < 100; i++) {
            invokeForwarded("/api/devices", "203.0.113.5");
        }
        // 101st with the same forwarded IP → limited
        assertThat(invokeForwarded("/api/devices", "203.0.113.5").getStatus()).isEqualTo(429);
        // different forwarded IP → not limited
        assertThat(invokeForwarded("/api/devices", "203.0.113.6").getStatus()).isNotEqualTo(429);
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
