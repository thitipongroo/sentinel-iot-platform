package com.sentinel.iot.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.util.StringUtils;

/**
 * Redis logical-database separation — defence against cascading failure.
 *
 * <p>All 4 Redis roles share the same Redis process in this configuration, but
 * use separate logical databases so that:
 * <ul>
 *   <li><b>DB 0 — telemetry cache + replay queue</b>: bulk read/write, replay buffer.
 *       If this DB slows down (e.g. large replay queue), it does NOT block auth checks.</li>
 *   <li><b>DB 1 — JWT blocklist (security-critical)</b>: low-volume writes (1 per logout),
 *       high-frequency reads (every authenticated request). Isolated so that cache
 *       or queue pressure never delays token revocation checks.</li>
 *   <li><b>Pub/sub</b>: uses a dedicated subscriber connection managed by
 *       {@link RedisWebSocketConfig} — already isolated by Spring Data Redis internals.</li>
 * </ul>
 *
 * <p><b>Production upgrade path (multi-instance):</b> replace the logical DB split
 * with separate Redis endpoints by adding {@code redis.auth.host}/{@code redis.auth.port}
 * env vars pointing at a separate ElastiCache cluster or Redis Sentinel group.
 * Only {@code authRedisConnectionFactory()} needs to change — no service code changes required.
 */
@Configuration
public class RedisConfig {

    @Value("${spring.data.redis.host:localhost}")
    private String host;

    @Value("${spring.data.redis.port:6379}")
    private int port;

    @Value("${spring.data.redis.password:}")
    private String password;

    // Override with a separate host/port to move auth to its own Redis instance in production
    @Value("${redis.auth.host:${spring.data.redis.host:localhost}}")
    private String authHost;

    @Value("${redis.auth.port:${spring.data.redis.port:6379}}")
    private int authPort;

    // ── DB 0 — telemetry cache, replay queue (primary / default) ─────────────

    @Primary
    @Bean("defaultRedisConnectionFactory")
    public RedisConnectionFactory defaultRedisConnectionFactory() {
        return buildConnectionFactory(host, port, 0);
    }

    @SuppressWarnings("null")
    @Primary
    @Bean("redisTemplate")
    public StringRedisTemplate redisTemplate(
            @org.springframework.beans.factory.annotation.Qualifier("defaultRedisConnectionFactory")
            RedisConnectionFactory factory) {
        return new StringRedisTemplate(factory);
    }

    // ── DB 1 — JWT token blocklist (security-critical, isolated) ─────────────

    @Bean("authRedisConnectionFactory")
    public RedisConnectionFactory authRedisConnectionFactory() {
        return buildConnectionFactory(authHost, authPort, 1);
    }

    @SuppressWarnings("null")
    @Bean("authRedisTemplate")
    public StringRedisTemplate authRedisTemplate(
            @org.springframework.beans.factory.annotation.Qualifier("authRedisConnectionFactory")
            RedisConnectionFactory factory) {
        return new StringRedisTemplate(factory);
    }

    // ── Internal ──────────────────────────────────────────────────────────────

    @SuppressWarnings("null")
    private LettuceConnectionFactory buildConnectionFactory(String redisHost, int redisPort, int db) {
        RedisStandaloneConfiguration config = new RedisStandaloneConfiguration(redisHost, redisPort);
        config.setDatabase(db);
        if (StringUtils.hasText(password)) {
            config.setPassword(password);
        }
        return new LettuceConnectionFactory(config);
    }
}
