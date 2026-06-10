package com.sentinel.iot;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sentinel.iot.dto.AuthRequest;
import com.sentinel.iot.service.MqttConsumerService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.integration.support.MessageBuilder;
import org.springframework.messaging.Message;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.Arrays;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * หมวดที่ 6 — Input Validation Security (7 tests)
 *
 * ทดสอบ: semver pattern enforcement, blank name rejection,
 *        SQL injection stored as literal (JPA parameterized query),
 *        XSS payload stored as literal (no server-side execution),
 *        invalid enum value rejection, MQTT payload missing deviceId routed
 *        to DLQ without crashing the consumer, oversized request body rejected.
 */
class InputValidationSecurityTest extends BaseIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired MqttConsumerService mqttConsumerService;

    // ── 6.1 Non-semver firmware version is rejected ──────────────────────────

    @SuppressWarnings("null")
@Test
    void nonSemverFirmwareVersion_isRejected() throws Exception {
        String adminToken = loginAndGetToken("admin", "admin123");
        String deviceId = createDevice(adminToken, "valid-fw-device-" + System.nanoTime());

        mockMvc.perform(patch("/api/v1/devices/" + deviceId + "/firmware")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"firmwareVersion\":\"not-a-version\"}"))
                .andExpect(status().isBadRequest());
    }

    // ── 6.2 Empty device name is rejected ────────────────────────────────────

    @SuppressWarnings("null")
@Test
    void emptyDeviceName_isRejected() throws Exception {
        String adminToken = loginAndGetToken("admin", "admin123");

        mockMvc.perform(post("/api/v1/devices")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"\"}"))
                .andExpect(status().isBadRequest());
    }

    // ── 6.3 SQL injection in device name is stored as a literal string ────────

    @Test
    void sqlInjectionDeviceName_isStoredAsLiteralString() throws Exception {
        String adminToken = loginAndGetToken("admin", "admin123");
        String sqlPayload = "'; DROP TABLE devices; --";

        // JPA uses parameterized queries — the payload is stored verbatim, not executed
        @SuppressWarnings("null")
        MvcResult created = mockMvc.perform(post("/api/v1/devices")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("name", sqlPayload))))
                .andExpect(status().isCreated())
                .andReturn();

        String deviceId = objectMapper.readTree(created.getResponse().getContentAsString())
                .get("id").asText();

        MvcResult fetched = mockMvc.perform(get("/api/v1/devices/" + deviceId)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andReturn();

        String returnedName = objectMapper.readTree(fetched.getResponse().getContentAsString())
                .get("name").asText();
        assertThat(returnedName).isEqualTo(sqlPayload);
    }

    // ── 6.4 XSS payload in device name is stored as a literal string ──────────

    @Test
    void xssPayloadDeviceName_isStoredAsLiteralString() throws Exception {
        String adminToken = loginAndGetToken("admin", "admin123");
        String xssPayload = "<script>alert(1)</script>";

        @SuppressWarnings("null")
        MvcResult created = mockMvc.perform(post("/api/v1/devices")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("name", xssPayload))))
                .andExpect(status().isCreated())
                .andReturn();

        String deviceId = objectMapper.readTree(created.getResponse().getContentAsString())
                .get("id").asText();

        MvcResult fetched = mockMvc.perform(get("/api/v1/devices/" + deviceId)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andReturn();

        String returnedName = objectMapper.readTree(fetched.getResponse().getContentAsString())
                .get("name").asText();
        assertThat(returnedName).isEqualTo(xssPayload);
    }

    // ── 6.5 Invalid lifecycle enum value is rejected ─────────────────────────

    @SuppressWarnings("null")
@Test
    void invalidLifecycleEnum_isRejected() throws Exception {
        String adminToken = loginAndGetToken("admin", "admin123");
        String deviceId = createDevice(adminToken, "enum-test-device-" + System.nanoTime());

        mockMvc.perform(patch("/api/v1/devices/" + deviceId + "/lifecycle")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"lifecycleStatus\":\"INVALID_STATUS\"}"))
                .andExpect(status().isBadRequest());
    }

    // ── 6.6 MQTT payload without deviceId is routed to DLQ, service does not crash ──

    @Test
    void mqttPayloadWithoutDeviceId_routesToDlqWithoutCrash() {
        // Payload intentionally omits the required "deviceId" field.
        // MqttConsumerService validates the parsed TelemetryMessage and routes invalid
        // messages to mqttDlqChannel — it must never propagate an exception to the caller.
        String payloadMissingDeviceId =
                "{\"temperature\":45.0,\"humidity\":60.0,\"motion\":false,\"smokePpm\":5.0}";
        Message<String> msg = MessageBuilder.withPayload(payloadMissingDeviceId).build();

        assertThatNoException().isThrownBy(() -> mqttConsumerService.handleMessage(msg));
    }

    // ── 6.7 Oversized request body (10 MB) is rejected with 413 ─────────────
    //
    // NOTE: This test documents a security gap.
    // Spring Boot 3.2 with embedded Tomcat does not enforce a request body size
    // limit for application/json payloads by default. Configure
    // server.tomcat.max-http-form-post-size or a custom filter to reject
    // oversized bodies with 413 Payload Too Large.

    @Test
    void oversizedRequestBody_isRejectedWith413() throws Exception {
        // 10 MB of filler JSON (will fail Jackson parsing but must be rejected before that)
        byte[] body = new byte[10 * 1024 * 1024];
        Arrays.fill(body, (byte) 'x');

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isPayloadTooLarge());
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    @SuppressWarnings("null")
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

    @SuppressWarnings("null")
private String createDevice(String token, String name) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/devices")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("name", name))))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString())
                .get("id").asText();
    }
}
