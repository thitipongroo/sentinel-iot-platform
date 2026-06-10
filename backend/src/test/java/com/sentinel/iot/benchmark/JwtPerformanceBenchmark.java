package com.sentinel.iot.benchmark;

import com.sentinel.iot.repository.RefreshTokenRepository;
import com.sentinel.iot.service.JwtService;
import org.mockito.Mockito;
import org.openjdk.jmh.annotations.*;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * JMH benchmark state for {@link JwtService} hot paths.
 *
 * Run via {@link PerformanceGateTest} (JUnit 5 wrapper) or directly:
 *   mvn test-compile exec:java -Dexec.mainClass=org.openjdk.jmh.Main \
 *       -Dexec.args="JwtPerformanceBenchmark"
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@State(Scope.Thread)
@Warmup(iterations = 3, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@Fork(1)
public class JwtPerformanceBenchmark {

    private JwtService jwtService;
    private UUID       orgId;
    private String     preGeneratedToken;

    @SuppressWarnings("null")
    @Setup(Level.Trial)
    public void setUp() {
        // Instantiate JwtService without Spring context — mocks satisfy the
        // constructor dependencies that are not exercised by these benchmarks.
        jwtService = new JwtService(
                Mockito.mock(RefreshTokenRepository.class),
                Mockito.mock(StringRedisTemplate.class));

        ReflectionTestUtils.setField(jwtService, "secret",
                "benchmark-secret-key-at-least-32-chars!");
        ReflectionTestUtils.setField(jwtService, "previousSecret", "");
        ReflectionTestUtils.setField(jwtService, "expirationMs",   900_000L);
        ReflectionTestUtils.setField(jwtService, "refreshExpirationMs", 604_800_000L);
        ReflectionTestUtils.setField(jwtService, "revocationKeyPrefix", "jwt:revoked:");

        orgId = UUID.randomUUID();
        preGeneratedToken = jwtService.generateAccessToken("admin", "ADMIN", orgId);
    }

    /** Measures the time to sign and compact a new JWT access token. */
    @Benchmark
    public String generateAccessToken() {
        return jwtService.generateAccessToken("admin", "ADMIN", orgId);
    }

    /** Measures the time to verify signature and extract the subject claim. */
    @Benchmark
    public String extractUsername() {
        return jwtService.extractUsername(preGeneratedToken);
    }

    /** Measures the combined validate-and-extract path (signature + expiry + claims). */
    @Benchmark
    public UUID extractOrgId() {
        return jwtService.extractOrgId(preGeneratedToken);
    }
}
