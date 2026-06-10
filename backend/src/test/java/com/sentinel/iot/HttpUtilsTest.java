package com.sentinel.iot;

import com.sentinel.iot.util.HttpUtils;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("unit")
@DisplayName("HttpUtils")
class HttpUtilsTest {

    // ── No proxy header ───────────────────────────────────────────────────────

    @Nested
    @DisplayName("Without X-Forwarded-For header")
    class WithoutForwardedHeader {

        @Test
        @DisplayName("returns remoteAddr when no X-Forwarded-For header is present")
        void resolveClientIp_returnsRemoteAddr_whenNoForwardedHeader() {
            MockHttpServletRequest request = new MockHttpServletRequest();
            request.setRemoteAddr("10.0.0.1");

            assertThat(HttpUtils.resolveClientIp(request)).isEqualTo("10.0.0.1");
        }
    }

    // ── With proxy header ─────────────────────────────────────────────────────

    @Nested
    @DisplayName("With X-Forwarded-For header")
    class WithForwardedHeader {

        @Test
        @DisplayName("returns the forwarded IP when header contains a single address")
        void resolveClientIp_returnsForwardedHeader_whenPresent() {
            MockHttpServletRequest request = new MockHttpServletRequest();
            request.setRemoteAddr("10.0.0.1");
            request.addHeader("X-Forwarded-For", "203.0.113.42");

            assertThat(HttpUtils.resolveClientIp(request)).isEqualTo("203.0.113.42");
        }

        @Test
        @DisplayName("returns only the first address when header contains a chain of IPs")
        void resolveClientIp_returnsFirstEntry_whenForwardedHeaderHasMultipleIps() {
            MockHttpServletRequest request = new MockHttpServletRequest();
            request.setRemoteAddr("10.0.0.1");
            request.addHeader("X-Forwarded-For", "203.0.113.42, 198.51.100.1, 10.0.0.2");

            assertThat(HttpUtils.resolveClientIp(request)).isEqualTo("203.0.113.42");
        }

        @ParameterizedTest(name = "header=''{0}'' → ''{1}''")
        @CsvSource({
            "'  203.0.113.42  , 198.51.100.1',  203.0.113.42",
            "'203.0.113.1',                      203.0.113.1",
            "'198.51.100.5, 10.0.0.3, 10.0.0.4', 198.51.100.5"
        })
        @DisplayName("trims whitespace and extracts first IP from various header formats")
        void resolveClientIp_handlesVariousHeaderFormats(String header, String expected) {
            MockHttpServletRequest request = new MockHttpServletRequest();
            request.addHeader("X-Forwarded-For", header);

            assertThat(HttpUtils.resolveClientIp(request)).isEqualTo(expected);
        }
    }
}
