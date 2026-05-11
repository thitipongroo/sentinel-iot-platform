package com.sentinel.iot.service;

import com.sentinel.iot.model.RefreshToken;
import com.sentinel.iot.repository.RefreshTokenRepository;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;

@Service
@RequiredArgsConstructor
public class JwtService {

    @Value("${jwt.secret}")
    private String secret;

    // Set during zero-downtime key rotation: export JWT_PREVIOUS_SECRET=<old-secret>
    // Tokens signed with the previous key remain valid until they expire, then rotation is complete.
    @Value("${jwt.previous-secret:}")
    private String previousSecret;

    @Value("${jwt.expiration-ms}")
    private long expirationMs;

    @Value("${jwt.refresh-expiration-ms}")
    private long refreshExpirationMs;

    @Value("${jwt.revocation-key-prefix:jwt:revoked:}")
    private String revocationKeyPrefix;

    private final RefreshTokenRepository refreshTokenRepository;
    private final StringRedisTemplate redis;

    // ── Access token generation ───────────────────────────────────────────────

    public String generateAccessToken(String username, String role, UUID orgId) {
        String jti = UUID.randomUUID().toString();
        return Jwts.builder()
                .header().add("kid", "current").and()
                .subject(username)
                .claims(Map.of(
                        "role",  role,
                        "orgId", orgId.toString(),
                        "jti",   jti
                ))
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + expirationMs))
                .signWith(currentKey())
                .compact();
    }

    // ── Access token revocation (Redis blocklist) ─────────────────────────────
    // Stores the token's JTI in Redis with TTL equal to the token's remaining lifetime.
    // JwtAuthFilter checks this blocklist on every authenticated request.
    // This closes the 15-minute window where a stolen or logged-out token stays valid.
    public void revokeAccessToken(String token) {
        try {
            Claims claims = parseClaims(token);
            String jti = claims.get("jti", String.class);
            Date expiration = claims.getExpiration();
            if (jti == null || expiration == null) return;
            long ttlSeconds = (expiration.getTime() - System.currentTimeMillis()) / 1000;
            if (ttlSeconds > 0) {
                redis.opsForValue().set(revocationKeyPrefix + jti, "1", Duration.ofSeconds(ttlSeconds));
            }
        } catch (Exception ignored) {
            // Malformed or already-expired token — nothing to revoke
        }
    }

    public boolean isAccessTokenRevoked(String token) {
        try {
            String jti = extractClaim(token, claims -> claims.get("jti", String.class));
            if (jti == null) return false;
            return Boolean.TRUE.equals(redis.hasKey(revocationKeyPrefix + jti));
        } catch (Exception e) {
            return false;
        }
    }

    // ── Refresh token operations ──────────────────────────────────────────────

    public UUID extractOrgId(String token) {
        String raw = extractClaim(token, claims -> claims.get("orgId", String.class));
        return raw != null ? UUID.fromString(raw) : null;
    }

    public RefreshToken generateRefreshToken(String username) {
        String tokenValue = UUID.randomUUID().toString() + "." + UUID.randomUUID().toString();
        Instant expiresAt = Instant.now().plusMillis(refreshExpirationMs);
        RefreshToken refreshToken = new RefreshToken(tokenValue, username, expiresAt);
        return refreshTokenRepository.save(refreshToken);
    }

    public RefreshToken rotateRefreshToken(String oldTokenValue) {
        RefreshToken existing = refreshTokenRepository.findByToken(oldTokenValue)
                .orElseThrow(() -> new IllegalArgumentException("Refresh token not found"));

        if (existing.isRevoked() || existing.isExpired()) {
            // Suspicious reuse — revoke all tokens for this user (RFC 6819 token family)
            refreshTokenRepository.revokeAllByUsername(existing.getUsername());
            throw new IllegalArgumentException("Refresh token is invalid or expired");
        }

        existing.setRevoked(true);
        refreshTokenRepository.save(existing);

        return generateRefreshToken(existing.getUsername());
    }

    public String getUsernameFromRefreshToken(String tokenValue) {
        RefreshToken token = refreshTokenRepository.findByToken(tokenValue)
                .orElseThrow(() -> new IllegalArgumentException("Refresh token not found"));
        if (token.isRevoked() || token.isExpired()) {
            throw new IllegalArgumentException("Refresh token is invalid or expired");
        }
        return token.getUsername();
    }

    public void revokeAllRefreshTokens(String username) {
        refreshTokenRepository.revokeAllByUsername(username);
    }

    // ── Token validation ──────────────────────────────────────────────────────

    public String extractUsername(String token) {
        return extractClaim(token, Claims::getSubject);
    }

    public boolean isTokenValid(String token, UserDetails userDetails) {
        return extractUsername(token).equals(userDetails.getUsername())
                && !isExpired(token)
                && !isAccessTokenRevoked(token);
    }

    // ── Key management (zero-downtime rotation) ───────────────────────────────
    // Rotation procedure:
    //   1. Set JWT_PREVIOUS_SECRET=<current-secret>
    //   2. Set JWT_SECRET=<new-secret>
    //   3. Deploy — new tokens use the new key; tokens signed with the old key remain valid.
    //   4. After jwt.expiration-ms (15 min) all old tokens have expired.
    //   5. Clear JWT_PREVIOUS_SECRET and redeploy.

    private SecretKey currentKey() {
        return Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    private SecretKey previousKey() {
        return Keys.hmacShaKeyFor(previousSecret.getBytes(StandardCharsets.UTF_8));
    }

    private Claims parseClaims(String token) {
        try {
            return Jwts.parser().verifyWith(currentKey()).build()
                    .parseSignedClaims(token).getPayload();
        } catch (JwtException | IllegalArgumentException e) {
            // During rotation window: try the previous key for tokens issued before the rotation
            if (StringUtils.hasText(previousSecret)) {
                return Jwts.parser().verifyWith(previousKey()).build()
                        .parseSignedClaims(token).getPayload();
            }
            throw e;
        }
    }

    private <T> T extractClaim(String token, Function<Claims, T> resolver) {
        return resolver.apply(parseClaims(token));
    }

    private boolean isExpired(String token) {
        return extractClaim(token, Claims::getExpiration).before(new Date());
    }

    @Scheduled(cron = "0 0 3 * * *")
    public void purgeExpiredTokens() {
        refreshTokenRepository.deleteExpiredAndRevoked(Instant.now());
    }
}
