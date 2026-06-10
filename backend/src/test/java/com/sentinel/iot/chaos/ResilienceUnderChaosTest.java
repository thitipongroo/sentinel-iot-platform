package com.sentinel.iot.chaos;

import eu.rekawek.toxiproxy.model.ToxicDirection;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.ToxiproxyContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.MountableFile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Chaos engineering tests — verify the application behaves correctly when
 * network faults are injected between the app and PostgreSQL via Toxiproxy.
 *
 * Scenarios covered:
 *   1. Baseline — normal operation through the proxy works correctly.
 *   2. Moderate latency (500 ms) — API still responds successfully.
 *   3. Severe latency (6 000 ms) — exceeds the JDBC socketTimeout (5 s),
 *      causing a timeout; API returns 5xx (not a hang or NPE).
 *   4. Recovery — after removing the fault, the API returns 200 again.
 *
 * Run with:  mvn test -Dgroups=chaos
 *
 * NOTE: This test starts its own Spring Boot context and four containers
 * (postgres + redis + mosquitto + toxiproxy) independently from
 * BaseIntegrationTest so faults can be injected without affecting other tests.
 */
@Tag("chaos")
@DisplayName("ResilienceUnderChaosTest — fault injection via Toxiproxy")
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ResilienceUnderChaosTest {

    // ── Shared Docker network so Toxiproxy can reach PostgreSQL by alias ──────

    static final Network network = Network.newNetwork();

    @SuppressWarnings("resource")
    @Container
    static final PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>("postgres:16-alpine")
                    .withNetwork(network)
                    .withNetworkAliases("postgres-chaos")
                    .withDatabaseName("sentinel_test")
                    .withUsername("test")
                    .withPassword("test");

    @SuppressWarnings("resource")
    @Container
    static final GenericContainer<?> redis =
            new GenericContainer<>("redis:7-alpine")
                    .withExposedPorts(6379)
                    .waitingFor(Wait.forListeningPort());

    @SuppressWarnings("resource")
    @Container
    static final GenericContainer<?> mosquitto =
            new GenericContainer<>("eclipse-mosquitto:2")
                    .withExposedPorts(1883)
                    .withCopyToContainer(
                            MountableFile.forClasspathResource("mosquitto-test.conf"),
                            "/mosquitto/config/mosquitto.conf")
                    .waitingFor(Wait.forListeningPort());

    @SuppressWarnings("resource")
    @Container
    static final ToxiproxyContainer toxiproxy =
            new ToxiproxyContainer("ghcr.io/shopify/toxiproxy:2.9.0")
                    .withNetwork(network);

    @SuppressWarnings("deprecation")
    static ToxiproxyContainer.ContainerProxy postgresProxy;

    // ── Dynamic properties — datasource routes through Toxiproxy ─────────────

    @SuppressWarnings("deprecation")
    @DynamicPropertySource
    static void configure(DynamicPropertyRegistry registry) {
        postgresProxy = toxiproxy.getProxy(postgres, 5432);

        // socketTimeout=5 → PostgreSQL JDBC driver raises SocketTimeoutException
        // if no data arrives within 5 seconds.  Severe Toxiproxy latency (> 5 s)
        // will trip this and propagate as DataAccessException → HTTP 5xx.
        String jdbcUrl = "jdbc:postgresql://"
                + postgresProxy.getContainerIpAddress() + ":"
                + postgresProxy.getProxyPort()
                + "/sentinel_test?socketTimeout=5";

        registry.add("spring.datasource.url",      () -> jdbcUrl);
        registry.add("spring.datasource.username", () -> "test");
        registry.add("spring.datasource.password", () -> "test");

        // HikariCP: minimumIdle=0 so there are no idle connections sitting in the
        // pool; each request acquires a fresh connection through Toxiproxy — ensuring
        // the injected fault is visible to every request immediately.
        registry.add("spring.datasource.hikari.minimum-idle",      () -> "0");
        registry.add("spring.datasource.hikari.maximum-pool-size",  () -> "5");
        registry.add("spring.datasource.hikari.connection-timeout", () -> "6000");

        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
        registry.add("mqtt.broker", () ->
                "tcp://" + mosquitto.getHost() + ":" + mosquitto.getMappedPort(1883));
        registry.add("mqtt.dlq-topic", () -> "factory/telemetry/dlq");
        registry.add("jwt.secret",     () -> "chaos-secret-key-at-least-32-chars-long!");
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    @Autowired MockMvc mockMvc;

    private static final String LATENCY_TOXIC = "postgres-latency";

    @SuppressWarnings("null")
    private String loginToken() throws Exception {
        var result = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"admin\",\"password\":\"admin123\"}"))
                .andExpect(status().isOk())
                .andReturn();
        return com.fasterxml.jackson.databind.json.JsonMapper.builder().build()
                .readTree(result.getResponse().getContentAsString())
                .get("accessToken").asText();
    }

    // ── Scenario 1: Baseline ──────────────────────────────────────────────────

    @Test
    @Order(1)
    @DisplayName("1 — baseline: API works correctly through Toxiproxy with no faults")
    void baseline_noFault_returns200() throws Exception {
        String token = loginToken();

        mockMvc.perform(get("/api/v1/devices")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }

    // ── Scenario 2: Moderate latency (500 ms) ────────────────────────────────

    @Test
    @Order(2)
    @DisplayName("2 — moderate latency (500 ms): API still returns 200")
    void moderateLatency_500ms_stillReturns200() throws Exception {
        postgresProxy.toxics()
                .latency(LATENCY_TOXIC, ToxicDirection.DOWNSTREAM, 500);
        try {
            String token = loginToken();

            mockMvc.perform(get("/api/v1/devices")
                            .header("Authorization", "Bearer " + token))
                    .andExpect(status().isOk());
        } finally {
            postgresProxy.toxics().get(LATENCY_TOXIC).remove();
        }
    }

    // ── Scenario 3: Severe latency — exceeds JDBC socketTimeout ──────────────

    @Test
    @Order(3)
    @DisplayName("3 — severe latency (6 000 ms > socketTimeout 5 s): API returns 5xx, not a hang")
    void severeLatency_exceedsSocketTimeout_returns5xx() throws Exception {
        // A pre-fetched token uses a direct Redis check (no DB latency for JWT validation).
        // The fault will manifest when the handler tries to query the device table.
        String token = loginToken();

        postgresProxy.toxics()
                .latency(LATENCY_TOXIC, ToxicDirection.DOWNSTREAM, 6_000);
        try {
            int status = mockMvc.perform(get("/api/v1/devices")
                            .header("Authorization", "Bearer " + token))
                    .andReturn()
                    .getResponse()
                    .getStatus();

            assertThat(status)
                    .as("severe DB latency must produce a 5xx response — never a 2xx or hang")
                    .isGreaterThanOrEqualTo(500)
                    .isLessThan(600);
        } finally {
            postgresProxy.toxics().get(LATENCY_TOXIC).remove();
        }
    }

    // ── Scenario 4: Recovery after fault removal ──────────────────────────────

    @Test
    @Order(4)
    @DisplayName("4 — recovery: API returns 200 immediately after fault is removed")
    void recovery_afterFaultRemoved_returns200() throws Exception {
        // Inject then immediately remove the fault to confirm clean state
        postgresProxy.toxics()
                .latency(LATENCY_TOXIC, ToxicDirection.DOWNSTREAM, 6_000);
        postgresProxy.toxics().get(LATENCY_TOXIC).remove();

        String token = loginToken();

        mockMvc.perform(get("/api/v1/devices")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }
}
