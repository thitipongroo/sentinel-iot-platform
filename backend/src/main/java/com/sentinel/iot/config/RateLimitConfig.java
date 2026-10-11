package com.sentinel.iot.config;

import io.github.bucket4j.distributed.ExpirationAfterWriteStrategy;
import io.github.bucket4j.distributed.proxy.ProxyManager;
import io.github.bucket4j.redis.lettuce.cas.LettuceBasedProxyManager;
import io.lettuce.core.RedisClient;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.cluster.RedisClusterClient;
import io.lettuce.core.cluster.api.StatefulRedisClusterConnection;
import io.lettuce.core.codec.ByteArrayCodec;
import io.lettuce.core.codec.RedisCodec;
import io.lettuce.core.codec.StringCodec;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;

import java.time.Duration;

/**
 * Distributed rate limiting configuration backed by Redis (LettuceBasedProxyManager).
 * Ensures rate limits are enforced across all horizontal replicas rather than in-process.
 */
@Configuration
@ConditionalOnClass({LettuceBasedProxyManager.class, RedisClient.class})
@Slf4j
public class RateLimitConfig {

    @Bean
    public ProxyManager<String> lettuceProxyManager(
            @Qualifier("defaultRedisConnectionFactory") RedisConnectionFactory connectionFactory) {
        try {
            if (connectionFactory instanceof LettuceConnectionFactory lettuceFactory) {
                if (lettuceFactory.getNativeClient() == null) {
                    lettuceFactory.afterPropertiesSet();
                    lettuceFactory.start();
                }
                Object nativeClient = lettuceFactory.getNativeClient();
                if (nativeClient instanceof RedisClient redisClient) {
                    StatefulRedisConnection<String, byte[]> connection = redisClient.connect(
                            RedisCodec.of(StringCodec.UTF8, ByteArrayCodec.INSTANCE)
                    );
                    log.info("Initialized distributed Redis-backed Bucket4j rate limiter (LettuceBasedProxyManager)");
                    return LettuceBasedProxyManager.builderFor(connection)
                            .withExpirationStrategy(ExpirationAfterWriteStrategy.basedOnTimeForRefillingBucketUpToMax(Duration.ofMinutes(2)))
                            .build();
                } else if (nativeClient instanceof RedisClusterClient clusterClient) {
                    StatefulRedisClusterConnection<String, byte[]> clusterConn = clusterClient.connect(
                            RedisCodec.of(StringCodec.UTF8, ByteArrayCodec.INSTANCE)
                    );
                    log.info("Initialized distributed Redis Cluster-backed Bucket4j rate limiter (LettuceBasedProxyManager)");
                    return LettuceBasedProxyManager.builderFor(clusterConn)
                            .withExpirationStrategy(ExpirationAfterWriteStrategy.basedOnTimeForRefillingBucketUpToMax(Duration.ofMinutes(2)))
                            .build();
                }
            }
        } catch (Exception e) {
            log.warn("Could not initialize LettuceBasedProxyManager: {}. Falling back to in-memory rate limiting.", e.getMessage());
        }
        return null;
    }
}
