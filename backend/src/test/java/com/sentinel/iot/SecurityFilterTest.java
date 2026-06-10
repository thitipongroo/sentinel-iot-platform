package com.sentinel.iot;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("SecurityFilter — authentication enforcement")
class SecurityFilterTest extends BaseIntegrationTest {

    // ── Authentication required ───────────────────────────────────────────────

    @Nested
    @DisplayName("Authentication required for protected endpoints")
    class AuthenticationRequired {

        @Test
        @DisplayName("request without an Authorization header returns HTTP 403")
        void requestWithoutToken_shouldReturn403() throws Exception {
            mockMvc.perform(get("/api/devices"))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("request with a malformed JWT token returns HTTP 403")
        void requestWithMalformedToken_shouldReturn403() throws Exception {
            mockMvc.perform(get("/api/devices")
                            .header("Authorization", "Bearer not.a.valid.jwt"))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("request using Basic auth scheme instead of Bearer is rejected with HTTP 403")
        void requestWithWrongScheme_shouldReturn403() throws Exception {
            mockMvc.perform(get("/api/devices")
                            .header("Authorization", "Basic dXNlcjpwYXNz"))
                    .andExpect(status().isForbidden());
        }
    }

    // ── Public endpoints ──────────────────────────────────────────────────────

    @Nested
    @DisplayName("Public endpoints (no auth required)")
    class PublicEndpoints {

        @Test
        @DisplayName("/actuator/health is publicly accessible")
        void healthEndpoint_shouldBePublic() throws Exception {
            mockMvc.perform(get("/actuator/health"))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("/api/v1/auth/login is publicly accessible (GET returns 405 Method Not Allowed)")
        void loginEndpoint_shouldBePublic() throws Exception {
            mockMvc.perform(get("/api/v1/auth/login"))
                    .andExpect(status().isMethodNotAllowed());
        }
    }
}
