package com.sentinel.iot;

import com.sentinel.iot.model.RefreshToken;
import com.sentinel.iot.repository.RefreshTokenRepository;
import com.sentinel.iot.service.JwtService;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class JwtServiceTest {

    @Mock RefreshTokenRepository refreshTokenRepository;
    @Mock StringRedisTemplate redis;
    @SuppressWarnings("rawtypes")
    @Mock ValueOperations valueOps;

    JwtService service;

    // HMAC-SHA256 requires >= 32-byte key
    private static final String SECRET      = "test-secret-key-that-is-32-bytes!!";
    private static final String SECRET_OLD  = "old-secret-key-for-rotation-test!!";
    private static final UUID   ORG_ID      = UUID.fromString("aaaaaaaa-0000-0000-0000-000000000001");

    @SuppressWarnings({"null", "unchecked"})
    @BeforeEach
    void setUp() {
        service = new JwtService(refreshTokenRepository, redis);
        ReflectionTestUtils.setField(service, "secret",              SECRET);
        ReflectionTestUtils.setField(service, "previousSecret",      "");
        ReflectionTestUtils.setField(service, "expirationMs",        900_000L);  // 15 min
        ReflectionTestUtils.setField(service, "refreshExpirationMs", 86_400_000L);
        ReflectionTestUtils.setField(service, "revocationKeyPrefix", "jwt:revoked:");
        lenient().when(redis.opsForValue()).thenReturn(valueOps);
    }

    // ---- generateAccessToken -----------------------------------------------

    @Test
    void generateAccessToken_isValidJwt_withCorrectSubject() {
        String token = service.generateAccessToken("alice", "ADMIN", ORG_ID);

        assertThat(service.extractUsername(token)).isEqualTo("alice");
    }

    @Test
    void generateAccessToken_containsRoleAndOrgIdClaims() {
        String token = service.generateAccessToken("alice", "ADMIN", ORG_ID);

        Claims claims = Jwts.parser()
                .verifyWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8)))
                .build()
                .parseSignedClaims(token)
                .getPayload();

        assertThat(claims.get("role", String.class)).isEqualTo("ADMIN");
        assertThat(claims.get("orgId", String.class)).isEqualTo(ORG_ID.toString());
        assertThat(claims.get("jti", String.class)).isNotBlank();
    }

    @Test
    void generateAccessToken_containsKidCurrentHeader() {
        String token = service.generateAccessToken("alice", "ADMIN", ORG_ID);

        // Header is the first Base64url segment before the first '.'
        String headerJson = new String(
                java.util.Base64.getUrlDecoder().decode(token.split("\\.")[0]),
                StandardCharsets.UTF_8);
        assertThat(headerJson).contains("\"kid\"").contains("current");
    }

    // ---- extractUsername ---------------------------------------------------

    @Test
    void extractUsername_returnsSubject() {
        String token = service.generateAccessToken("bob", "OPERATOR", ORG_ID);
        assertThat(service.extractUsername(token)).isEqualTo("bob");
    }

    // ---- extractOrgId ------------------------------------------------------

    @Test
    void extractOrgId_returnsOrgIdFromClaim() {
        String token = service.generateAccessToken("alice", "ADMIN", ORG_ID);
        assertThat(service.extractOrgId(token)).isEqualTo(ORG_ID);
    }

    // ---- revokeAccessToken -------------------------------------------------

    @SuppressWarnings({"null", "unchecked"})
    @Test
    void revokeAccessToken_storesJtiInRedis_withPositiveTtl() {
        String token = service.generateAccessToken("alice", "ADMIN", ORG_ID);

        service.revokeAccessToken(token);

        verify(valueOps).set(
                argThat(key -> key.toString().startsWith("jwt:revoked:")),
                eq("1"),
                argThat(d -> ((Duration) d).getSeconds() > 0));
    }

    @Test
    void revokeAccessToken_malformedToken_doesNotThrow() {
        assertThatNoException().isThrownBy(
                () -> service.revokeAccessToken("not.a.valid.jwt"));
    }

    @Test
    void revokeAccessToken_alreadyExpiredToken_doesNotThrow() {
        assertThatNoException().isThrownBy(
                () -> service.revokeAccessToken("eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJ0ZXN0IiwiZXhwIjoxfQ.fake"));
    }

    // ---- isAccessTokenRevoked ----------------------------------------------

    @SuppressWarnings("null")
    @Test
    void isAccessTokenRevoked_returnsFalse_whenJtiNotInRedis() {
        String token = service.generateAccessToken("alice", "ADMIN", ORG_ID);
        when(redis.hasKey(anyString())).thenReturn(false);

        assertThat(service.isAccessTokenRevoked(token)).isFalse();
    }

    @SuppressWarnings("null")
    @Test
    void isAccessTokenRevoked_returnsTrue_whenJtiInRedis() {
        String token = service.generateAccessToken("alice", "ADMIN", ORG_ID);
        when(redis.hasKey(anyString())).thenReturn(true);

        assertThat(service.isAccessTokenRevoked(token)).isTrue();
    }

    // ---- isTokenValid ------------------------------------------------------

    @SuppressWarnings("null")
    @Test
    void isTokenValid_returnsTrue_forValidNonRevokedToken() {
        String token = service.generateAccessToken("alice", "ADMIN", ORG_ID);
        when(redis.hasKey(anyString())).thenReturn(false);
        UserDetails userDetails = User.withUsername("alice")
                .password("x").authorities("ROLE_ADMIN").build();

        assertThat(service.isTokenValid(token, userDetails)).isTrue();
    }

    @SuppressWarnings("null")
    @Test
    void isTokenValid_returnsFalse_whenRevoked() {
        String token = service.generateAccessToken("alice", "ADMIN", ORG_ID);
        when(redis.hasKey(anyString())).thenReturn(true);
        UserDetails userDetails = User.withUsername("alice")
                .password("x").authorities("ROLE_ADMIN").build();

        assertThat(service.isTokenValid(token, userDetails)).isFalse();
    }

    @SuppressWarnings("null")
    @Test
    void isTokenValid_returnsFalse_whenUsernameDoesNotMatch() {
        String token = service.generateAccessToken("alice", "ADMIN", ORG_ID);
        when(redis.hasKey(anyString())).thenReturn(false);
        UserDetails userDetails = User.withUsername("bob")
                .password("x").authorities("ROLE_ADMIN").build();

        assertThat(service.isTokenValid(token, userDetails)).isFalse();
    }

    // ---- dual-key rotation fallback ----------------------------------------

    @SuppressWarnings("null")
    @Test
    void extractUsername_succeedsWithPreviousKey_duringRotation() {
        // Generate token with the OLD key
        ReflectionTestUtils.setField(service, "secret", SECRET_OLD);
        String tokenSignedWithOldKey = service.generateAccessToken("alice", "ADMIN", ORG_ID);

        // Simulate key rotation: new current key, old becomes previousSecret
        ReflectionTestUtils.setField(service, "secret",         SECRET);
        ReflectionTestUtils.setField(service, "previousSecret", SECRET_OLD);

        // Token signed with the old key must still be parseable
        assertThat(service.extractUsername(tokenSignedWithOldKey)).isEqualTo("alice");
    }

    // ---- generateRefreshToken ----------------------------------------------

    @SuppressWarnings("null")
    @Test
    void generateRefreshToken_savesHashedToken_andSetsRawToken() {
        when(refreshTokenRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        RefreshToken rt = service.generateRefreshToken("alice");

        assertThat(rt.getRawToken()).isNotBlank();
        assertThat(rt.getToken()).isNotEqualTo(rt.getRawToken()); // stored hash != raw
        assertThat(rt.getUsername()).isEqualTo("alice");
        assertThat(rt.getExpiresAt()).isAfter(Instant.now());
    }

    // ---- revokeAllRefreshTokens -------------------------------------------

    @SuppressWarnings("null")
    @Test
    void revokeAllRefreshTokens_delegatesToRepository() {
        service.revokeAllRefreshTokens("alice");
        verify(refreshTokenRepository).revokeAllByUsername("alice");
    }

    // ---- purgeExpiredTokens ------------------------------------------------

    @SuppressWarnings("null")
    @Test
    void purgeExpiredTokens_callsDeleteExpiredAndRevoked() {
        service.purgeExpiredTokens();
        verify(refreshTokenRepository).deleteExpiredAndRevoked(any(Instant.class));
    }

    // ---- rotateRefreshToken ------------------------------------------------

    @SuppressWarnings("null")
    @Test
    void rotateRefreshToken_revokesSuspiciousReuse_whenTokenAlreadyRevoked() {
        String rawToken = "stale-raw-token";
        RefreshToken existing = new RefreshToken("hash", "alice", Instant.now().plusSeconds(3600));
        existing.setRevoked(true);
        when(refreshTokenRepository.findByToken(any())).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> service.rotateRefreshToken(rawToken))
                .isInstanceOf(IllegalArgumentException.class);
        verify(refreshTokenRepository).revokeAllByUsername("alice");
    }

    @SuppressWarnings("null")
    @Test
    void rotateRefreshToken_issuesNewToken_whenCurrentIsValid() {
        String rawToken = "valid-raw-token";
        RefreshToken existing = new RefreshToken("hash", "alice", Instant.now().plusSeconds(3600));
        when(refreshTokenRepository.findByToken(any())).thenReturn(Optional.of(existing));
        when(refreshTokenRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        RefreshToken newToken = service.rotateRefreshToken(rawToken);

        assertThat(newToken.getUsername()).isEqualTo("alice");
        assertThat(existing.isRevoked()).isTrue(); // old token marked revoked
    }
}
