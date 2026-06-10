package com.sentinel.iot;

import com.sentinel.iot.model.RefreshToken;
import com.sentinel.iot.repository.RefreshTokenRepository;
import com.sentinel.iot.service.JwtService;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
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

@Tag("unit")
@DisplayName("JwtService")
@ExtendWith(MockitoExtension.class)
class JwtServiceTest {

    @Mock RefreshTokenRepository refreshTokenRepository;
    @Mock StringRedisTemplate redis;
    @SuppressWarnings("rawtypes")
    @Mock ValueOperations valueOps;

    JwtService service;

    private static final String SECRET     = "test-secret-key-that-is-32-bytes!!";
    private static final String SECRET_OLD = "old-secret-key-for-rotation-test!!";
    private static final UUID   ORG_ID     = UUID.fromString("aaaaaaaa-0000-0000-0000-000000000001");

    @SuppressWarnings({"null", "unchecked"})
    @BeforeEach
    void setUp() {
        service = new JwtService(refreshTokenRepository, redis);
        ReflectionTestUtils.setField(service, "secret",              SECRET);
        ReflectionTestUtils.setField(service, "previousSecret",      "");
        ReflectionTestUtils.setField(service, "expirationMs",        900_000L);
        ReflectionTestUtils.setField(service, "refreshExpirationMs", 86_400_000L);
        ReflectionTestUtils.setField(service, "revocationKeyPrefix", "jwt:revoked:");
        lenient().when(redis.opsForValue()).thenReturn(valueOps);
    }

    // ── Token generation ──────────────────────────────────────────────────────

    @Nested
    @DisplayName("generateAccessToken()")
    class GenerateAccessToken {

        @Test
        @DisplayName("produces a JWT whose subject matches the username")
        void generateAccessToken_subjectMatchesUsername() {
            String token = service.generateAccessToken("alice", "ADMIN", ORG_ID);

            assertThat(service.extractUsername(token)).isEqualTo("alice");
        }

        @Test
        @DisplayName("embeds role, orgId, and jti claims")
        void generateAccessToken_containsRoleOrgIdAndJti() {
            String token = service.generateAccessToken("alice", "ADMIN", ORG_ID);

            Claims claims = Jwts.parser()
                    .verifyWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8)))
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();

            assertThat(claims.get("role", String.class)).as("role claim").isEqualTo("ADMIN");
            assertThat(claims.get("orgId", String.class)).as("orgId claim").isEqualTo(ORG_ID.toString());
            assertThat(claims.get("jti", String.class)).as("jti claim").isNotBlank();
        }

        @Test
        @DisplayName("sets kid=current in JWT header")
        void generateAccessToken_setsKidCurrentInHeader() {
            String token = service.generateAccessToken("alice", "ADMIN", ORG_ID);

            String headerJson = new String(
                    java.util.Base64.getUrlDecoder().decode(token.split("\\.")[0]),
                    StandardCharsets.UTF_8);

            assertThat(headerJson).contains("\"kid\"").contains("current");
        }
    }

    // ── Claim extraction ──────────────────────────────────────────────────────

    @Nested
    @DisplayName("extractUsername() / extractOrgId()")
    class ClaimExtraction {

        @Test
        @DisplayName("extractUsername returns the token subject")
        void extractUsername_returnsSubject() {
            String token = service.generateAccessToken("bob", "OPERATOR", ORG_ID);

            assertThat(service.extractUsername(token)).isEqualTo("bob");
        }

        @Test
        @DisplayName("extractOrgId returns the orgId claim as UUID")
        void extractOrgId_returnsOrgIdFromClaim() {
            String token = service.generateAccessToken("alice", "ADMIN", ORG_ID);

            assertThat(service.extractOrgId(token)).isEqualTo(ORG_ID);
        }
    }

    // ── Access token revocation ───────────────────────────────────────────────

    @Nested
    @DisplayName("revokeAccessToken()")
    class RevokeAccessToken {

        @SuppressWarnings({"null", "unchecked"})
        @Test
        @DisplayName("stores jti in Redis with a positive TTL")
        void revokeAccessToken_storesJtiInRedis_withPositiveTtl() {
            String token = service.generateAccessToken("alice", "ADMIN", ORG_ID);

            service.revokeAccessToken(token);

            verify(valueOps).set(
                    argThat(key -> key.toString().startsWith("jwt:revoked:")),
                    eq("1"),
                    argThat(d -> ((Duration) d).getSeconds() > 0));
        }

        @Test
        @DisplayName("does not throw on malformed token")
        void revokeAccessToken_malformedToken_doesNotThrow() {
            assertThatNoException().isThrownBy(
                    () -> service.revokeAccessToken("not.a.valid.jwt"));
        }

        @Test
        @DisplayName("does not throw on already-expired token")
        void revokeAccessToken_alreadyExpiredToken_doesNotThrow() {
            assertThatNoException().isThrownBy(
                    () -> service.revokeAccessToken(
                            "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJ0ZXN0IiwiZXhwIjoxfQ.fake"));
        }
    }

    // ── Revocation check ──────────────────────────────────────────────────────

    @Nested
    @DisplayName("isAccessTokenRevoked()")
    class IsAccessTokenRevoked {

        @SuppressWarnings("null")
        @Test
        @DisplayName("returns false when jti is not in Redis")
        void isAccessTokenRevoked_returnsFalse_whenJtiNotInRedis() {
            String token = service.generateAccessToken("alice", "ADMIN", ORG_ID);
            when(redis.hasKey(anyString())).thenReturn(false);

            assertThat(service.isAccessTokenRevoked(token)).isFalse();
        }

        @SuppressWarnings("null")
        @Test
        @DisplayName("returns true when jti is present in Redis")
        void isAccessTokenRevoked_returnsTrue_whenJtiInRedis() {
            String token = service.generateAccessToken("alice", "ADMIN", ORG_ID);
            when(redis.hasKey(anyString())).thenReturn(true);

            assertThat(service.isAccessTokenRevoked(token)).isTrue();
        }
    }

    // ── Token validation ──────────────────────────────────────────────────────

    @Nested
    @DisplayName("isTokenValid()")
    class IsTokenValid {

        @SuppressWarnings("null")
        @Test
        @DisplayName("returns true for a valid, non-revoked token with matching username")
        void isTokenValid_returnsTrue_forValidNonRevokedToken() {
            String token = service.generateAccessToken("alice", "ADMIN", ORG_ID);
            when(redis.hasKey(anyString())).thenReturn(false);
            UserDetails userDetails = User.withUsername("alice")
                    .password("x").authorities("ROLE_ADMIN").build();

            assertThat(service.isTokenValid(token, userDetails)).isTrue();
        }

        @SuppressWarnings("null")
        @Test
        @DisplayName("returns false when token is revoked")
        void isTokenValid_returnsFalse_whenRevoked() {
            String token = service.generateAccessToken("alice", "ADMIN", ORG_ID);
            when(redis.hasKey(anyString())).thenReturn(true);
            UserDetails userDetails = User.withUsername("alice")
                    .password("x").authorities("ROLE_ADMIN").build();

            assertThat(service.isTokenValid(token, userDetails)).isFalse();
        }

        @SuppressWarnings("null")
        @Test
        @DisplayName("returns false when token subject does not match UserDetails")
        void isTokenValid_returnsFalse_whenUsernameDoesNotMatch() {
            String token = service.generateAccessToken("alice", "ADMIN", ORG_ID);
            when(redis.hasKey(anyString())).thenReturn(false);
            UserDetails userDetails = User.withUsername("bob")
                    .password("x").authorities("ROLE_ADMIN").build();

            assertThat(service.isTokenValid(token, userDetails)).isFalse();
        }
    }

    // ── Key rotation ──────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Key rotation (dual-key fallback)")
    class KeyRotation {

        @SuppressWarnings("null")
        @Test
        @DisplayName("accepts a token signed with the previous key during rotation window")
        void extractUsername_succeedsWithPreviousKey_duringRotation() {
            ReflectionTestUtils.setField(service, "secret", SECRET_OLD);
            String tokenSignedWithOldKey = service.generateAccessToken("alice", "ADMIN", ORG_ID);

            ReflectionTestUtils.setField(service, "secret",         SECRET);
            ReflectionTestUtils.setField(service, "previousSecret", SECRET_OLD);

            assertThat(service.extractUsername(tokenSignedWithOldKey))
                    .as("old-key token must be parseable during rotation")
                    .isEqualTo("alice");
        }
    }

    // ── Refresh token management ──────────────────────────────────────────────

    @Nested
    @DisplayName("Refresh token management")
    class RefreshTokenManagement {

        @SuppressWarnings("null")
        @Test
        @DisplayName("generateRefreshToken saves a hashed token and exposes the raw value")
        void generateRefreshToken_savesHashedToken_andSetsRawToken() {
            when(refreshTokenRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            RefreshToken rt = service.generateRefreshToken("alice");

            assertThat(rt.getRawToken()).as("raw token").isNotBlank();
            assertThat(rt.getToken()).as("stored hash != raw token").isNotEqualTo(rt.getRawToken());
            assertThat(rt.getUsername()).isEqualTo("alice");
            assertThat(rt.getExpiresAt()).isAfter(Instant.now());
        }

        @SuppressWarnings("null")
        @Test
        @DisplayName("revokeAllRefreshTokens delegates to repository")
        void revokeAllRefreshTokens_delegatesToRepository() {
            service.revokeAllRefreshTokens("alice");

            verify(refreshTokenRepository).revokeAllByUsername("alice");
        }

        @SuppressWarnings("null")
        @Test
        @DisplayName("purgeExpiredTokens deletes expired-and-revoked entries")
        void purgeExpiredTokens_callsDeleteExpiredAndRevoked() {
            service.purgeExpiredTokens();

            verify(refreshTokenRepository).deleteExpiredAndRevoked(any(Instant.class));
        }

        @SuppressWarnings("null")
        @Test
        @DisplayName("rotateRefreshToken revokes all tokens for user on suspicious reuse (already-revoked token)")
        void rotateRefreshToken_revokesSuspiciousReuse_whenTokenAlreadyRevoked() {
            RefreshToken existing = new RefreshToken("hash", "alice", Instant.now().plusSeconds(3600));
            existing.setRevoked(true);
            when(refreshTokenRepository.findByToken(any())).thenReturn(Optional.of(existing));

            assertThatThrownBy(() -> service.rotateRefreshToken("stale-raw-token"))
                    .isInstanceOf(IllegalArgumentException.class);
            verify(refreshTokenRepository).revokeAllByUsername("alice");
        }

        @SuppressWarnings("null")
        @Test
        @DisplayName("rotateRefreshToken issues a new token and marks old one revoked")
        void rotateRefreshToken_issuesNewToken_whenCurrentIsValid() {
            RefreshToken existing = new RefreshToken("hash", "alice", Instant.now().plusSeconds(3600));
            when(refreshTokenRepository.findByToken(any())).thenReturn(Optional.of(existing));
            when(refreshTokenRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            RefreshToken newToken = service.rotateRefreshToken("valid-raw-token");

            assertThat(newToken.getUsername()).isEqualTo("alice");
            assertThat(existing.isRevoked()).as("old token must be marked revoked").isTrue();
        }
    }
}
