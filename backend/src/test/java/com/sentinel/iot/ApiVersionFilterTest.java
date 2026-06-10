package com.sentinel.iot;

import com.sentinel.iot.security.ApiVersionFilter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;

class ApiVersionFilterTest {

    private ApiVersionFilter filter;

    @BeforeEach
    void setUp() {
        filter = new ApiVersionFilter();
    }

    @Test
    void versionedPath_setsApiVersionHeader_noDeprecationHeaders() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/devices");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertThat(response.getHeader("API-Version")).isEqualTo("1");
        assertThat(response.getHeader("Deprecation")).isNull();
        assertThat(response.getHeader("Sunset")).isNull();
        assertThat(response.getHeader("Link")).isNull();
    }

    @Test
    void unversionedPath_setsApiVersionAndDeprecationHeaders() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/devices");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertThat(response.getHeader("API-Version")).isEqualTo("1");
        assertThat(response.getHeader("Deprecation")).isEqualTo("true");
        assertThat(response.getHeader("Sunset")).isNotBlank();
        assertThat(response.getHeader("Link")).contains("/api/v1/devices");
    }

    @Test
    void unversionedPath_linkHeader_pointsToV1Equivalent() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/telemetry/ingest");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertThat(response.getHeader("Link"))
                .isEqualTo("</api/v1/telemetry/ingest>; rel=\"successor-version\"");
    }

    @Test
    void v2Path_isVersioned_noDeprecationHeaders() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v2/sensors");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertThat(response.getHeader("API-Version")).isEqualTo("1");
        assertThat(response.getHeader("Deprecation")).isNull();
    }

    @Test
    void nonApiPath_filterIsSkipped_noApiVersionHeader() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/health");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertThat(response.getHeader("API-Version")).isNull();
    }

    @Test
    void nonApiPath_actuatorEndpoint_filterIsSkipped() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/actuator/health");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertThat(response.getHeader("API-Version")).isNull();
    }
}
