package com.sentinel.iot;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class SecurityFilterTest extends BaseIntegrationTest {

    @Autowired MockMvc mockMvc;

    @Test
    void requestWithoutToken_shouldReturn403() throws Exception {
        mockMvc.perform(get("/api/devices"))
                .andExpect(status().isForbidden());
    }

    @Test
    void requestWithMalformedToken_shouldReturn403() throws Exception {
        mockMvc.perform(get("/api/devices")
                        .header("Authorization", "Bearer not.a.valid.jwt"))
                .andExpect(status().isForbidden());
    }

    @Test
    void requestWithWrongScheme_shouldReturn403() throws Exception {
        mockMvc.perform(get("/api/devices")
                        .header("Authorization", "Basic dXNlcjpwYXNz"))
                .andExpect(status().isForbidden());
    }

    @Test
    void healthEndpoint_shouldBePublic() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk());
    }

    @Test
    void loginEndpoint_shouldBePublic() throws Exception {
        mockMvc.perform(get("/api/auth/login"))
                .andExpect(status().isMethodNotAllowed());
    }
}
