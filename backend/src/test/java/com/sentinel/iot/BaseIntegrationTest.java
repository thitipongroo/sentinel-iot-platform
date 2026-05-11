package com.sentinel.iot;

import org.junit.jupiter.api.TestInstance;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.MountableFile;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public abstract class BaseIntegrationTest {

    @SuppressWarnings("resource")
    @Container
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("sentinel_test")
            .withUsername("test")
            .withPassword("test")
            .withReuse(true);

    @SuppressWarnings("resource")
    @Container
    static final GenericContainer<?> redis = new GenericContainer<>("redis:7-alpine")
            .withExposedPorts(6379)
            .withReuse(true)
            .waitingFor(Wait.forListeningPort());

    @SuppressWarnings("resource")
    @Container
    static final GenericContainer<?> mosquitto = new GenericContainer<>("eclipse-mosquitto:2")
            .withExposedPorts(1883)
            .withReuse(true)
            .withCopyToContainer(
                MountableFile.forClasspathResource("mosquitto-test.conf"),
                "/mosquitto/config/mosquitto.conf"
            )
            .waitingFor(Wait.forListeningPort());

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
}
