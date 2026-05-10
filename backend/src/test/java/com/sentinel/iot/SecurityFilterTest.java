package com.sentinel.iot;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class SecurityFilterTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("sentinel_test")
            .withUsername("test")
            .withPassword("test");

    @DynamicPropertySource
    static void configure(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.data.redis.host", () -> "localhost");
        registry.add("mqtt.broker", () -> "tcp://localhost:1883");
        registry.add("jwt.secret", () -> "test-secret-key-at-least-32-chars-long!");
    }

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
        // Should reach the endpoint (401 from auth, not 403 from security filter)
        mockMvc.perform(get("/api/auth/login"))
                .andExpect(status().isMethodNotAllowed()); // GET is not mapped, but route is reachable
    }
}
