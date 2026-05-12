package com.sentinel.iot;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sentinel.iot.dto.AuthRequest;
import com.sentinel.iot.repository.AppUserRepository;
import com.sentinel.iot.service.JwtService;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import javax.crypto.SecretKey;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
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
class JwtSecurityTest extends BaseIntegrationTest {

    private static final String TEST_SECRET = "test-secret-key-at-least-32-chars-long!";

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired JwtService jwtService;
    @Autowired AppUserRepository userRepository;

    // ── 1.1 alg:none bypass ──────────────────────────────────────────────────

    @Test
    void algNoneToken_isRejected() throws Exception {
        String header  = Base64.getUrlEncoder().withoutPadding()
                .encodeToString("{\"alg\":\"none\",\"typ\":\"JWT\"}".getBytes());
        String payload = Base64.getUrlEncoder().withoutPadding()
                .encodeToString("{\"sub\":\"admin\",\"role\":\"ADMIN\"}".getBytes());
        String noneToken = header + "." + payload + ".";

        mockMvc.perform(get("/api/v1/devices")
                        .header("Authorization", "Bearer " + noneToken))
                .andExpect(status().isForbidden());
    }

    // ── 1.2 Tampered signature ───────────────────────────────────────────────

    @Test
    void tamperedSignature_isRejected() throws Exception {
        String token = loginAndGetToken("admin", "admin123");
        String[] parts = token.split("\\.");
        String tampered = parts[0] + "." + parts[1] + ".invalidsignaturexxx";

        mockMvc.perform(get("/api/v1/devices")
                        .header("Authorization", "Bearer " + tampered))
                .andExpect(status().isForbidden());
    }

    // ── 1.3 Expired token ────────────────────────────────────────────────────

    @Test
    void expiredToken_isRejected() throws Exception {
        UUID orgId = getAdminOrgId();
        String expired = Jwts.builder()
                .subject("admin")
                .claims(Map.of(
                        "role",  "ADMIN",
                        "orgId", orgId.toString(),
                        "jti",   UUID.randomUUID().toString()))
                .issuedAt(new Date(System.currentTimeMillis() - 2_000_000))
                .expiration(new Date(System.currentTimeMillis() - 1_000_000))
                .signWith(testKey())
                .compact();

        mockMvc.perform(get("/api/v1/devices")
                        .header("Authorization", "Bearer " + expired))
                .andExpect(status().isForbidden());
    }

    // ── 1.4 Revoked token (via logout) ───────────────────────────────────────

    @Test
    void revokedToken_afterLogout_isRejected() throws Exception {
        String token = loginAndGetToken("admin", "admin123");

        mockMvc.perform(post("/api/v1/auth/logout")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());

        // Token should now be in the Redis JTI blocklist
        mockMvc.perform(get("/api/v1/devices")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    // ── 1.5 Cross-org token cannot access other org's device ─────────────────

    @Test
    void tokenWithForeignOrgId_cannotAccessOtherOrgsDevice() throws Exception {
        String adminToken = loginAndGetToken("admin", "admin123");

        // Create device under admin's (default) org
        String deviceId = createDevice(adminToken, "idor-test-device-" + System.nanoTime());

        // Generate a valid JWT but for a non-existent foreign org
        UUID foreignOrgId = UUID.randomUUID();
        String foreignToken = jwtService.generateAccessToken("admin", "ADMIN", foreignOrgId);

        // Device belongs to default org; RLS hides it when queried with foreign orgId
        mockMvc.perform(get("/api/v1/devices/" + deviceId)
                        .header("Authorization", "Bearer " + foreignToken))
                .andExpect(status().isNotFound());
    }

    // ── 1.6 Token for non-existent user is rejected ──────────────────────────

    @Test
    void tokenForNonExistentUser_isRejected() throws Exception {
        String token = Jwts.builder()
                .subject("ghost-user-that-does-not-exist")
                .claims(Map.of(
                        "role",  "ADMIN",
                        "orgId", UUID.randomUUID().toString(),
                        "jti",   UUID.randomUUID().toString()))
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + 600_000))
                .signWith(testKey())
                .compact();

        // JwtAuthFilter: valid signature → extractUsername → loadUserByUsername throws
        // → no authentication set → 403
        mockMvc.perform(get("/api/v1/devices")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    // ── 1.7 Token signed with wrong secret is rejected ───────────────────────

    @Test
    void tokenSignedWithWrongSecret_isRejected() throws Exception {
        UUID orgId = getAdminOrgId();
        SecretKey wrongKey = Keys.hmacShaKeyFor(
                "a-totally-wrong-secret-key-that-is-32-chars!!".getBytes(StandardCharsets.UTF_8));

        String forgedToken = Jwts.builder()
                .subject("admin")
                .claims(Map.of(
                        "role",  "ADMIN",
                        "orgId", orgId.toString(),
                        "jti",   UUID.randomUUID().toString()))
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

    // ── 1.8 Token signed with previous key accepted during key rotation ───────

    @Test
    void tokenSignedWithPreviousKey_isAcceptedDuringRotation() throws Exception {
        String prevSecret = "previous-rotation-key-that-is-32-chars!!";
        SecretKey prevKey = Keys.hmacShaKeyFor(prevSecret.getBytes(StandardCharsets.UTF_8));
        UUID orgId = getAdminOrgId();

        String prevToken = Jwts.builder()
                .subject("admin")
                .claims(Map.of(
                        "role",  "ADMIN",
                        "orgId", orgId.toString(),
                        "jti",   UUID.randomUUID().toString()))
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + 600_000))
                .signWith(prevKey)
                .compact();

        // Configure JwtService to accept tokens signed with the previous key
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

    // ── 1.9 Non-Bearer scheme is rejected ────────────────────────────────────

    @Test
    void basicAuthScheme_isRejected() throws Exception {
        // JwtAuthFilter only processes "Bearer " tokens; Basic auth is ignored
        mockMvc.perform(get("/api/v1/devices")
                        .header("Authorization", "Basic YWRtaW46YWRtaW4xMjM="))
                .andExpect(status().isForbidden());
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private String loginAndGetToken(String username, String password) throws Exception {
        AuthRequest req = new AuthRequest();
        req.setUsername(username);
        req.setPassword(password);

        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andReturn();

        return objectMapper.readTree(result.getResponse().getContentAsString())
                .get("accessToken").asText();
    }

    private UUID getAdminOrgId() {
        return userRepository.findByUsername("admin")
                .orElseThrow(() -> new IllegalStateException("admin user not seeded"))
                .getOrganizationId();
    }

    private String createDevice(String token, String name) throws Exception {
        String body = objectMapper.writeValueAsString(Map.of("name", name));
        MvcResult result = mockMvc.perform(post("/api/v1/devices")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString())
                .get("id").asText();
    }

    private static SecretKey testKey() {
        return Keys.hmacShaKeyFor(TEST_SECRET.getBytes(StandardCharsets.UTF_8));
    }
}
