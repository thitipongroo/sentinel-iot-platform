package com.sentinel.iot;

import com.sentinel.iot.repository.AppUserRepository;
import com.sentinel.iot.service.JwtService;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import javax.crypto.SecretKey;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.Map;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * หมวดที่ 1 — Authentication & JWT Security (9 tests)
 *
 * ทดสอบ: alg:none bypass, tampered signature, expired token, revoked token,
 *        cross-org access, non-existent user, wrong-secret forgery,
 *        key rotation (previous-secret), non-Bearer scheme.
 */
@DisplayName("JWT Security — authentication attack surface")
class JwtSecurityTest extends BaseIntegrationTest {

    // The test secret mirrors jwt.secret configured in BaseIntegrationTest @DynamicPropertySource
    private static final String TEST_SECRET = "test-secret-key-at-least-32-chars-long!";

    @Autowired JwtService         jwtService;
    @Autowired AppUserRepository  userRepository;

    // ── Token forgery / bypass ────────────────────────────────────────────────

    @Nested
    @DisplayName("Token forgery and bypass attacks")
    class TokenForgery {

        @Test
        @DisplayName("alg:none token is rejected even when signature section is empty")
        void algNoneToken_isRejected() throws Exception {
            String header  = java.util.Base64.getUrlEncoder().withoutPadding()
                    .encodeToString("{\"alg\":\"none\",\"typ\":\"JWT\"}".getBytes());
            String payload = java.util.Base64.getUrlEncoder().withoutPadding()
                    .encodeToString("{\"sub\":\"admin\",\"role\":\"ADMIN\"}".getBytes());
            String noneToken = header + "." + payload + ".";

            mockMvc.perform(get("/api/v1/devices")
                            .header("Authorization", "Bearer " + noneToken))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("token with a tampered signature is rejected")
        void tamperedSignature_isRejected() throws Exception {
            String token = loginAndGetToken("admin", "admin123");
            String[] parts = token.split("\\.");
            String tampered = parts[0] + "." + parts[1] + ".invalidsignaturexxx";

            mockMvc.perform(get("/api/v1/devices")
                            .header("Authorization", "Bearer " + tampered))
                    .andExpect(status().isForbidden());
        }

        @SuppressWarnings("null")
        @Test
        @DisplayName("token signed with a wrong secret is rejected")
        void tokenSignedWithWrongSecret_isRejected() throws Exception {
            UUID orgId = getAdminOrgId();
            SecretKey wrongKey = Keys.hmacShaKeyFor(
                    "a-totally-wrong-secret-key-that-is-32-chars!!".getBytes(StandardCharsets.UTF_8));

            String forgedToken = Jwts.builder()
                    .subject("admin")
                    .claims(Map.of("role", "ADMIN", "orgId", orgId.toString(), "jti", UUID.randomUUID().toString()))
                    .issuedAt(new Date())
                    .expiration(new Date(System.currentTimeMillis() + 600_000))
                    .signWith(wrongKey)
                    .compact();

            mockMvc.perform(post("/api/v1/devices")
                            .header("Authorization", "Bearer " + forgedToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"name\":\"forged-device\"}"))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("non-Bearer Authorization scheme (Basic) is rejected")
        void basicAuthScheme_isRejected() throws Exception {
            // JwtAuthFilter only processes "Bearer " tokens
            mockMvc.perform(get("/api/v1/devices")
                            .header("Authorization", "Basic YWRtaW46YWRtaW4xMjM="))
                    .andExpect(status().isForbidden());
        }
    }

    // ── Token validity ────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Token validity checks")
    class TokenValidity {

        @Test
        @DisplayName("expired token is rejected")
        void expiredToken_isRejected() throws Exception {
            UUID orgId = getAdminOrgId();
            @SuppressWarnings("null")
            String expired = Jwts.builder()
                    .subject("admin")
                    .claims(Map.of("role", "ADMIN", "orgId", orgId.toString(), "jti", UUID.randomUUID().toString()))
                    .issuedAt(new Date(System.currentTimeMillis() - 2_000_000))
                    .expiration(new Date(System.currentTimeMillis() - 1_000_000))
                    .signWith(testKey())
                    .compact();

            mockMvc.perform(get("/api/v1/devices")
                            .header("Authorization", "Bearer " + expired))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("token for a non-existent user is rejected")
        void tokenForNonExistentUser_isRejected() throws Exception {
            @SuppressWarnings("null")
            String token = Jwts.builder()
                    .subject("ghost-user-that-does-not-exist")
                    .claims(Map.of("role", "ADMIN", "orgId", UUID.randomUUID().toString(), "jti", UUID.randomUUID().toString()))
                    .issuedAt(new Date())
                    .expiration(new Date(System.currentTimeMillis() + 600_000))
                    .signWith(testKey())
                    .compact();

            // Valid signature but unknown username → loadUserByUsername throws → no auth → 403
            mockMvc.perform(get("/api/v1/devices")
                            .header("Authorization", "Bearer " + token))
                    .andExpect(status().isForbidden());
        }
    }

    // ── Revocation ────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Token revocation")
    class TokenRevocation {

        @Test
        @DisplayName("access token is rejected after the owning user logs out")
        void revokedToken_afterLogout_isRejected() throws Exception {
            String token = loginAndGetToken("admin", "admin123");

            mockMvc.perform(post("/api/v1/auth/logout")
                            .header("Authorization", "Bearer " + token))
                    .andExpect(status().isNoContent());

            // Token is now in the Redis JTI blocklist
            mockMvc.perform(get("/api/v1/devices")
                            .header("Authorization", "Bearer " + token))
                    .andExpect(status().isForbidden());
        }
    }

    // ── Tenant isolation ──────────────────────────────────────────────────────

    @Nested
    @DisplayName("Cross-org (IDOR) protection")
    class CrossOrgProtection {

        @Test
        @DisplayName("valid JWT with a foreign orgId cannot access another org's device")
        void tokenWithForeignOrgId_cannotAccessOtherOrgsDevice() throws Exception {
            String adminToken = loginAndGetToken("admin", "admin123");
            String deviceId = createDevice(adminToken, "idor-test-device-" + System.nanoTime());

            String foreignToken = jwtService.generateAccessToken("admin", "ADMIN", UUID.randomUUID());

            // Device belongs to admin's org; RLS hides it under the foreign orgId
            mockMvc.perform(get("/api/v1/devices/" + deviceId)
                            .header("Authorization", "Bearer " + foreignToken))
                    .andExpect(status().isNotFound());
        }
    }

    // ── Key rotation ──────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Key rotation")
    class KeyRotation {

        @Test
        @DisplayName("token signed with the previous key is accepted while previousSecret is configured")
        void tokenSignedWithPreviousKey_isAcceptedDuringRotation() throws Exception {
            String prevSecret = "previous-rotation-key-that-is-32-chars!!";
            SecretKey prevKey = Keys.hmacShaKeyFor(prevSecret.getBytes(StandardCharsets.UTF_8));
            UUID orgId = getAdminOrgId();

            @SuppressWarnings("null")
            String prevToken = Jwts.builder()
                    .subject("admin")
                    .claims(Map.of("role", "ADMIN", "orgId", orgId.toString(), "jti", UUID.randomUUID().toString()))
                    .issuedAt(new Date())
                    .expiration(new Date(System.currentTimeMillis() + 600_000))
                    .signWith(prevKey)
                    .compact();

            Field prevSecretField = JwtService.class.getDeclaredField("previousSecret");
            prevSecretField.setAccessible(true);
            prevSecretField.set(jwtService, prevSecret);
            try {
                mockMvc.perform(get("/api/v1/devices")
                                .header("Authorization", "Bearer " + prevToken))
                        .andExpect(status().isOk());
            } finally {
                prevSecretField.set(jwtService, "");
            }
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private UUID getAdminOrgId() {
        return userRepository.findByUsername("admin")
                .orElseThrow(() -> new IllegalStateException("admin user not seeded"))
                .getOrganizationId();
    }

    private static SecretKey testKey() {
        return Keys.hmacShaKeyFor(TEST_SECRET.getBytes(StandardCharsets.UTF_8));
    }
}
