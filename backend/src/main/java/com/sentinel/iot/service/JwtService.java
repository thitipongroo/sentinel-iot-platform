package com.sentinel.iot.service;

import com.sentinel.iot.model.RefreshToken;
import com.sentinel.iot.repository.RefreshTokenRepository;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
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

    @Value("${jwt.expiration-ms}")
    private long expirationMs;

    @Value("${jwt.refresh-expiration-ms}")
    private long refreshExpirationMs;

    private final RefreshTokenRepository refreshTokenRepository;

    public String generateAccessToken(String username, String role, UUID orgId) {
        return Jwts.builder()
                .subject(username)
                .claims(Map.of("role", role, "orgId", orgId.toString()))
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + expirationMs))
                .signWith(getKey())
                .compact();
    }

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
            // Revoke all tokens for this user on suspicious reuse
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

    public String extractUsername(String token) {
        return extractClaim(token, Claims::getSubject);
    }

    public boolean isTokenValid(String token, UserDetails userDetails) {
        return extractUsername(token).equals(userDetails.getUsername()) && !isExpired(token);
    }

    private boolean isExpired(String token) {
        return extractClaim(token, Claims::getExpiration).before(new Date());
    }

    private <T> T extractClaim(String token, Function<Claims, T> resolver) {
        return resolver.apply(Jwts.parser().verifyWith(getKey()).build().parseSignedClaims(token).getPayload());
    }

    private SecretKey getKey() {
        return Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    @Scheduled(cron = "0 0 3 * * *")
    public void purgeExpiredTokens() {
        refreshTokenRepository.deleteExpiredAndRevoked(Instant.now());
    }
}
