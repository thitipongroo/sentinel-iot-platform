package com.sentinel.iot;

import com.sentinel.iot.util.HttpUtils;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;

class HttpUtilsTest {

    @Test
    void resolveClientIp_returnsRemoteAddr_whenNoForwardedHeader() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("10.0.0.1");

        assertThat(HttpUtils.resolveClientIp(request)).isEqualTo("10.0.0.1");
    }

    @Test
    void resolveClientIp_returnsForwardedHeader_whenPresent() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("10.0.0.1");
        request.addHeader("X-Forwarded-For", "203.0.113.42");

        assertThat(HttpUtils.resolveClientIp(request)).isEqualTo("203.0.113.42");
    }

    @Test
    void resolveClientIp_returnsFirstEntry_whenForwardedHeaderHasMultipleIps() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("10.0.0.1");
        request.addHeader("X-Forwarded-For", "203.0.113.42, 198.51.100.1, 10.0.0.2");

        assertThat(HttpUtils.resolveClientIp(request)).isEqualTo("203.0.113.42");
    }

    @Test
    void resolveClientIp_trims_whitespaceFromForwardedHeader() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Forwarded-For", "  203.0.113.42  , 198.51.100.1");

        assertThat(HttpUtils.resolveClientIp(request)).isEqualTo("203.0.113.42");
    }
}
