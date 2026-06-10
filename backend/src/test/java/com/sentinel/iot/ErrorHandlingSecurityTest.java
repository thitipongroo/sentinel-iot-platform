package com.sentinel.iot;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * หมวดที่ 9 — Error Handling & Information Disclosure (4 tests)
 *
 * ทดสอบ: user enumeration prevention, no stack trace in error responses,
 *        nonexistent endpoint returns 404 without leaking internal paths,
 *        Swagger UI is accessible in all profiles (documented gap).
 */
@DisplayName("Error Handling Security — information disclosure prevention")
class ErrorHandlingSecurityTest extends BaseIntegrationTest {

    // ── User enumeration prevention ───────────────────────────────────────────

    @Nested
    @DisplayName("User enumeration prevention")
    class UserEnumeration {

        @SuppressWarnings("null")
        @Test
        @DisplayName("unknown username and wrong password return identical HTTP status (no enumeration)")
        void loginFailure_sameStatusForUnknownUserAndWrongPassword() throws Exception {
            // Spring DaoAuthenticationProvider hides UsernameNotFoundException as
            // BadCredentialsException — both paths return the same HTTP status
            var unknownUser = mockMvc.perform(post("/api/v1/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(
                                    authRequest("nonexistent-xyz", "anypassword"))))
                    .andReturn();

            var wrongPassword = mockMvc.perform(post("/api/v1/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(
                                    authRequest("admin", "wrong-password-xyz"))))
                    .andReturn();

            assertThat(unknownUser.getResponse().getStatus())
                    .as("unknown user and wrong password must return the same status")
                    .isEqualTo(wrongPassword.getResponse().getStatus());
        }
    }

    // ── Information disclosure prevention ─────────────────────────────────────

    @Nested
    @DisplayName("Information disclosure prevention")
    class InformationDisclosure {

        @SuppressWarnings("null")
        @Test
        @DisplayName("validation error response body does not contain a stack trace")
        void validationError_doesNotExposeStackTrace() throws Exception {
            String adminToken = loginAndGetToken("admin", "admin123");

            var result = mockMvc.perform(post("/api/v1/devices")
                            .header("Authorization", "Bearer " + adminToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"name\":\"\"}"))
                    .andExpect(status().isBadRequest())
                    .andReturn();

            assertThat(result.getResponse().getContentAsString())
                    .as("error response must not contain stack trace fragments")
                    .doesNotContain("at com.", "stackTrace", "StackTrace");
        }

        @SuppressWarnings("null")
        @Test
        @DisplayName("nonexistent endpoint returns 404 without leaking internal class paths")
        void nonexistentEndpoint_returns404WithoutInternalPaths() throws Exception {
            String adminToken = loginAndGetToken("admin", "admin123");

            var result = mockMvc.perform(get("/api/v1/nonexistent-endpoint-xyz")
                            .header("Authorization", "Bearer " + adminToken))
                    .andReturn();

            assertThat(result.getResponse().getStatus()).as("status must be 404").isEqualTo(404);
            assertThat(result.getResponse().getContentAsString())
                    .as("404 body must not contain internal paths or stack traces")
                    .doesNotContain("at com.sentinel", "stackTrace");
        }
    }

    // ── Documented gaps ───────────────────────────────────────────────────────

    @Nested
    @DisplayName("Documented security gaps")
    class DocumentedGaps {

        // SECURITY GAP: OpenApiConfig has no @Profile annotation and SecurityConfig always
        // permits /swagger-ui/**, /swagger, /api-docs/**. Production deployments should
        // disable Swagger by activating a "prod" profile that excludes OpenApiConfig.
        @Test
        @DisplayName("Swagger UI is accessible without authentication (documented gap — disable in prod)")
        void swaggerUi_isAccessibleWithoutAuth_documentedGap() throws Exception {
            mockMvc.perform(get("/swagger"))
                    .andExpect(result ->
                            assertThat(result.getResponse().getStatus())
                                    .as("Swagger must be accessible — documents gap: " +
                                        "disable with spring.profiles.active=prod")
                                    .isLessThan(400));
        }
    }
}
