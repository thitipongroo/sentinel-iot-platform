package com.sentinel.iot;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sentinel.iot.dto.AuthRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * หมวดที่ 9 — Error Handling & Information Disclosure (3 tests)
 *
 * ทดสอบ: user enumeration prevention (same status for unknown user vs wrong password),
 *        no stack trace in error responses,
 *        nonexistent endpoint returns 404 without leaking internal paths.
 */
class ErrorHandlingSecurityTest extends BaseIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;

    // ── 9.1 Login failure returns identical status for unknown user and wrong password ──

    @Test
    void loginFailure_sameStatusForUnknownUserAndWrongPassword() throws Exception {
        // Spring DaoAuthenticationProvider hides UsernameNotFoundException as
        // BadCredentialsException by default — both paths return the same HTTP status
        MvcResult unknownUser = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(authRequest("nonexistent-xyz", "anypassword"))))
                .andReturn();

        MvcResult wrongPassword = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(authRequest("admin", "wrong-password-xyz"))))
                .andReturn();

        assertThat(unknownUser.getResponse().getStatus())
                .isEqualTo(wrongPassword.getResponse().getStatus());
    }

    // ── 9.2 Validation error response does not expose a stack trace ───────────

    @Test
    void validationError_doesNotExposeStackTrace() throws Exception {
        String adminToken = loginAndGetToken("admin", "admin123");

        // Submitting an empty name triggers @NotBlank — GlobalExceptionHandler returns ProblemDetail
        MvcResult result = mockMvc.perform(post("/api/v1/devices")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andReturn();

        String body = result.getResponse().getContentAsString();
        assertThat(body).doesNotContain("at com.", "stackTrace", "StackTrace");
    }

    // ── 9.3 Nonexistent endpoint returns 404 without leaking internal paths ───

    @Test
    void nonexistentEndpoint_returns404WithoutInternalPaths() throws Exception {
        String adminToken = loginAndGetToken("admin", "admin123");

        MvcResult result = mockMvc.perform(get("/api/v1/nonexistent-endpoint-xyz")
                        .header("Authorization", "Bearer " + adminToken))
                .andReturn();

        assertThat(result.getResponse().getStatus()).isEqualTo(404);
        String body = result.getResponse().getContentAsString();
        assertThat(body).doesNotContain("at com.sentinel", "stackTrace");
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private String loginAndGetToken(String username, String password) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(authRequest(username, password))))
                .andExpect(status().isOk())
                .andReturn();

        return objectMapper.readTree(result.getResponse().getContentAsString())
                .get("accessToken").asText();
    }

    private AuthRequest authRequest(String username, String password) {
        AuthRequest req = new AuthRequest();
        req.setUsername(username);
        req.setPassword(password);
        return req;
    }
}
