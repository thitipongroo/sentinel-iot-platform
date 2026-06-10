package com.sentinel.iot;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sentinel.iot.dto.AuthRequest;
import org.junit.jupiter.api.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.MountableFile;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Shared base for all Spring Boot integration tests.
 *
 * Provides:
 * - Singleton TestContainers (Postgres, Redis, Mosquitto) shared across all tests
 *   to avoid repeated container startup overhead.
 * - Common HTTP helpers (login, device creation) so each test class does not
 *   re-implement the same boilerplate.
 *
 * All subclasses are tagged "integration" and can be excluded from fast local
 * feedback loops with: {@code mvn test -Dgroups=unit}
 */
@SpringBootTest
@AutoConfigureMockMvc
@Tag("integration")
public abstract class BaseIntegrationTest {

    // ── Singleton containers (started once for the entire test suite) ─────────

    @SuppressWarnings("resource")
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("sentinel_test")
            .withUsername("test")
            .withPassword("test");

    @SuppressWarnings("resource")
    static final GenericContainer<?> redis = new GenericContainer<>("redis:7-alpine")
            .withExposedPorts(6379)
            .waitingFor(Wait.forListeningPort());

    @SuppressWarnings("resource")
    static final GenericContainer<?> mosquitto = new GenericContainer<>("eclipse-mosquitto:2")
            .withExposedPorts(1883)
            .withCopyToContainer(
                MountableFile.forClasspathResource("mosquitto-test.conf"),
                "/mosquitto/config/mosquitto.conf"
            )
            .waitingFor(Wait.forListeningPort());

    static {
        postgres.start();
        redis.start();
        mosquitto.start();
    }

    @DynamicPropertySource
    static void configure(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
        registry.add("mqtt.broker", () ->
            "tcp://" + mosquitto.getHost() + ":" + mosquitto.getMappedPort(1883));
        registry.add("mqtt.dlq-topic", () -> "factory/telemetry/dlq");
        registry.add("jwt.secret", () -> "test-secret-key-at-least-32-chars-long!");
    }

    // ── Shared Spring beans available to all subclasses ───────────────────────

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected ObjectMapper objectMapper;

    // ── Reusable HTTP helpers ─────────────────────────────────────────────────

    /**
     * Performs a login and returns the access token from the response body.
     * Fails the test if login does not return HTTP 200.
     */
    @SuppressWarnings("null")
    protected String loginAndGetToken(String username, String password) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(authRequest(username, password))))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString())
                .get("accessToken").asText();
    }

    /**
     * Creates a device as admin and returns its UUID string.
     * Fails the test if creation does not return HTTP 201.
     */
    @SuppressWarnings("null")
    protected String createDevice(String token, String name) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/devices")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(java.util.Map.of("name", name))))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString())
                .get("id").asText();
    }

    /** Builds an {@link AuthRequest} DTO from plain username/password strings. */
    protected AuthRequest authRequest(String username, String password) {
        AuthRequest req = new AuthRequest();
        req.setUsername(username);
        req.setPassword(password);
        return req;
    }
}
