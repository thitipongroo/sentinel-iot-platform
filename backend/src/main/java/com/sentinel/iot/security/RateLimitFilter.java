package com.sentinel.iot.security;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.util.Arrays;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Per-IP rate limiter with tiered limits:
 *   - Auth/enroll endpoints: 10 req/min  (brute-force protection)
 *   - General API:          100 req/min
 *
 * X-Forwarded-For is only trusted when the direct peer matches a configured
 * trusted-proxy list ({@code rate-limit.trusted-proxies}).  Without that
 * setting (e.g. local dev), the TCP remote address is always used as the
 * client identity — preventing IP spoofing via crafted headers.
 *
 * For multi-instance deployments, replace the ConcurrentHashMap with a
 * Redis-backed Bucket4j ProxyManager (bucket4j-redis) so the limits are
 * enforced across all instances rather than per-process.
 */
@Component
public class RateLimitFilter extends OncePerRequestFilter {

    private static final int  GENERAL_CAPACITY   = 100;
    private static final int  AUTH_CAPACITY       = 10;
    private static final Duration REFILL_PERIOD   = Duration.ofMinutes(1);

    private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();

    @Value("${rate-limit.trusted-proxies:}")
    private String trustedProxiesConfig;

    private volatile Set<String> trustedProxies;

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain chain) throws ServletException, IOException {
        String path = request.getRequestURI();
        if (!path.startsWith("/api/")) {
            chain.doFilter(request, response);
            return;
        }

        boolean isAuthPath   = isAuthEndpoint(path);
        String  clientIp     = resolveClientIp(request);
        String  bucketKey    = (isAuthPath ? "auth:" : "api:") + clientIp;
        int     capacity     = isAuthPath ? AUTH_CAPACITY : GENERAL_CAPACITY;
        Bucket  bucket       = buckets.computeIfAbsent(bucketKey, k -> newBucket(capacity));

        if (bucket.tryConsume(1)) {
            chain.doFilter(request, response);
        } else {
            String limit = isAuthPath ? "10" : "100";
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.setContentType("application/json");
            response.getWriter().write(
                    "{\"error\":\"Rate limit exceeded. Max " + limit + " requests/minute.\"}");
        }
    }

    private boolean isAuthEndpoint(String path) {
        return path.startsWith("/api/v1/auth/") || path.equals("/api/v1/devices/enroll");
    }

    private Bucket newBucket(int capacity) {
        return Bucket.builder()
                .addLimit(Bandwidth.builder()
                        .capacity(capacity)
                        .refillGreedy(capacity, REFILL_PERIOD)
                        .build())
                .build();
    }

    private String resolveClientIp(HttpServletRequest request) {
        String remoteAddr = request.getRemoteAddr();
        String forwarded  = request.getHeader("X-Forwarded-For");
        if (forwarded != null && isTrustedProxy(remoteAddr)) {
            return forwarded.split(",")[0].trim();
        }
        return remoteAddr;
    }

    private boolean isTrustedProxy(String remoteAddr) {
        if (trustedProxies == null) {
            // Lazy-init: parse comma-separated IPs from config
            String cfg = trustedProxiesConfig;
            trustedProxies = (cfg == null || cfg.isBlank())
                    ? Set.of()
                    : Arrays.stream(cfg.split(","))
                             .map(String::trim)
                             .filter(s -> !s.isBlank())
                             .collect(Collectors.toUnmodifiableSet());
        }
        return trustedProxies.contains(remoteAddr);
    }
}
