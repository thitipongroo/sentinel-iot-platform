package com.sentinel.iot;

import com.sentinel.iot.security.ApiVersionFilter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("unit")
@DisplayName("ApiVersionFilter")
class ApiVersionFilterTest {

    private ApiVersionFilter filter;

    @BeforeEach
    void setUp() {
        filter = new ApiVersionFilter();
    }

    // ── Versioned paths ───────────────────────────────────────────────────────

    @Nested
    @DisplayName("Versioned paths (/api/vN/...)")
    class VersionedPaths {

        @Test
        @DisplayName("sets API-Version header and does not add deprecation headers for /api/v1")
        void versionedV1Path_setsApiVersionHeader_noDeprecationHeaders() throws Exception {
            MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/devices");
            MockHttpServletResponse response = new MockHttpServletResponse();

            filter.doFilter(request, response, new MockFilterChain());

            assertThat(response.getHeader("API-Version")).isEqualTo("1");
            assertThat(response.getHeader("Deprecation")).isNull();
            assertThat(response.getHeader("Sunset")).isNull();
            assertThat(response.getHeader("Link")).isNull();
        }

        @Test
        @DisplayName("treats /api/v2 as versioned — no deprecation headers")
        void versionedV2Path_noDeprecationHeaders() throws Exception {
            MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v2/sensors");
            MockHttpServletResponse response = new MockHttpServletResponse();

            filter.doFilter(request, response, new MockFilterChain());

            assertThat(response.getHeader("API-Version")).isEqualTo("1");
            assertThat(response.getHeader("Deprecation")).isNull();
        }
    }

    // ── Unversioned / legacy paths ────────────────────────────────────────────

    @Nested
    @DisplayName("Unversioned / legacy paths (/api/...)")
    class UnversionedPaths {

        @Test
        @DisplayName("adds Deprecation, Sunset, and Link headers for /api/devices")
        void unversionedPath_setsDeprecationHeaders() throws Exception {
            MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/devices");
            MockHttpServletResponse response = new MockHttpServletResponse();

            filter.doFilter(request, response, new MockFilterChain());

            assertThat(response.getHeader("API-Version")).isEqualTo("1");
            assertThat(response.getHeader("Deprecation")).isEqualTo("true");
            assertThat(response.getHeader("Sunset")).isNotBlank();
            assertThat(response.getHeader("Link")).contains("/api/v1/devices");
        }

        @Test
        @DisplayName("Link header points to the v1 successor of the requested path")
        void unversionedPath_linkHeaderPointsToV1Equivalent() throws Exception {
            MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/telemetry/ingest");
            MockHttpServletResponse response = new MockHttpServletResponse();

            filter.doFilter(request, response, new MockFilterChain());

            assertThat(response.getHeader("Link"))
                    .isEqualTo("</api/v1/telemetry/ingest>; rel=\"successor-version\"");
        }
    }

    // ── Non-API paths ─────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Non-API paths (filter is skipped)")
    class NonApiPaths {

        @Test
        @DisplayName("no API-Version header is added for /health")
        void nonApiPath_filterIsSkipped() throws Exception {
            MockHttpServletRequest request = new MockHttpServletRequest("GET", "/health");
            MockHttpServletResponse response = new MockHttpServletResponse();

            filter.doFilter(request, response, new MockFilterChain());

            assertThat(response.getHeader("API-Version")).isNull();
        }

        @Test
        @DisplayName("no API-Version header is added for /actuator/health")
        void actuatorPath_filterIsSkipped() throws Exception {
            MockHttpServletRequest request = new MockHttpServletRequest("GET", "/actuator/health");
            MockHttpServletResponse response = new MockHttpServletResponse();

            filter.doFilter(request, response, new MockFilterChain());

            assertThat(response.getHeader("API-Version")).isNull();
        }
    }
}
