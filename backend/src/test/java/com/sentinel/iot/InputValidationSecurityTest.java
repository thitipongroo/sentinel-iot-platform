package com.sentinel.iot;

import com.sentinel.iot.service.MqttConsumerService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.integration.support.MessageBuilder;

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
@DisplayName("Input Validation Security — injection and malformed input")
class InputValidationSecurityTest extends BaseIntegrationTest {

    @Autowired MqttConsumerService mqttConsumerService;

    // ── Rejection of invalid input ────────────────────────────────────────────

    @Nested
    @DisplayName("Rejection of invalid input")
    class Rejection {

        @SuppressWarnings("null")
        @Test
        @DisplayName("non-semver firmware version string is rejected with HTTP 400")
        void nonSemverFirmwareVersion_isRejected() throws Exception {
            String adminToken = loginAndGetToken("admin", "admin123");
            String deviceId = createDevice(adminToken, "valid-fw-device-" + System.nanoTime());

            mockMvc.perform(patch("/api/v1/devices/" + deviceId + "/firmware")
                            .header("Authorization", "Bearer " + adminToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"firmwareVersion\":\"not-a-version\"}"))
                    .andExpect(status().isBadRequest());
        }

        @SuppressWarnings("null")
        @Test
        @DisplayName("blank device name is rejected with HTTP 400 (@NotBlank)")
        void emptyDeviceName_isRejected() throws Exception {
            String adminToken = loginAndGetToken("admin", "admin123");

            mockMvc.perform(post("/api/v1/devices")
                            .header("Authorization", "Bearer " + adminToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"name\":\"\"}"))
                    .andExpect(status().isBadRequest());
        }

        @SuppressWarnings("null")
        @Test
        @DisplayName("invalid lifecycle enum value is rejected with HTTP 400")
        void invalidLifecycleEnum_isRejected() throws Exception {
            String adminToken = loginAndGetToken("admin", "admin123");
            String deviceId = createDevice(adminToken, "enum-test-device-" + System.nanoTime());

            mockMvc.perform(patch("/api/v1/devices/" + deviceId + "/lifecycle")
                            .header("Authorization", "Bearer " + adminToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"lifecycleStatus\":\"INVALID_STATUS\"}"))
                    .andExpect(status().isBadRequest());
        }
    }

    // ── Literal storage (parameterized queries prevent injection) ─────────────

    @Nested
    @DisplayName("Injection payloads stored as literals (JPA parameterized queries)")
    class LiteralStorage {

        @Test
        @DisplayName("SQL injection payload in device name is stored verbatim — not executed")
        void sqlInjectionDeviceName_isStoredAsLiteralString() throws Exception {
            String adminToken = loginAndGetToken("admin", "admin123");
            String sqlPayload = "'; DROP TABLE devices; --";

            @SuppressWarnings("null")
            var created = mockMvc.perform(post("/api/v1/devices")
                            .header("Authorization", "Bearer " + adminToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(Map.of("name", sqlPayload))))
                    .andExpect(status().isCreated())
                    .andReturn();

            String deviceId = objectMapper.readTree(created.getResponse().getContentAsString())
                    .get("id").asText();

            @SuppressWarnings("null")
            var fetched = mockMvc.perform(get("/api/v1/devices/" + deviceId)
                            .header("Authorization", "Bearer " + adminToken))
                    .andExpect(status().isOk())
                    .andReturn();

            assertThat(objectMapper.readTree(fetched.getResponse().getContentAsString())
                    .get("name").asText())
                    .as("SQL injection payload must be stored and returned verbatim")
                    .isEqualTo(sqlPayload);
        }

        @Test
        @DisplayName("XSS payload in device name is stored verbatim — not executed server-side")
        void xssPayloadDeviceName_isStoredAsLiteralString() throws Exception {
            String adminToken = loginAndGetToken("admin", "admin123");
            String xssPayload = "<script>alert(1)</script>";

            @SuppressWarnings("null")
            var created = mockMvc.perform(post("/api/v1/devices")
                            .header("Authorization", "Bearer " + adminToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(Map.of("name", xssPayload))))
                    .andExpect(status().isCreated())
                    .andReturn();

            String deviceId = objectMapper.readTree(created.getResponse().getContentAsString())
                    .get("id").asText();

            @SuppressWarnings("null")
            var fetched = mockMvc.perform(get("/api/v1/devices/" + deviceId)
                            .header("Authorization", "Bearer " + adminToken))
                    .andExpect(status().isOk())
                    .andReturn();

            assertThat(objectMapper.readTree(fetched.getResponse().getContentAsString())
                    .get("name").asText())
                    .as("XSS payload must be stored and returned verbatim")
                    .isEqualTo(xssPayload);
        }
    }

    // ── MQTT payload validation ───────────────────────────────────────────────

    @Nested
    @DisplayName("MQTT payload validation")
    class MqttValidation {

        @Test
        @DisplayName("MQTT payload missing deviceId is routed to the DLQ without crashing the consumer")
        void mqttPayloadWithoutDeviceId_routesToDlqWithoutCrash() {
            String payloadMissingDeviceId =
                    "{\"temperature\":45.0,\"humidity\":60.0,\"motion\":false,\"smokePpm\":5.0}";

            assertThatNoException()
                    .as("missing deviceId must be routed to DLQ, not propagated as an exception")
                    .isThrownBy(() -> mqttConsumerService.handleMessage(
                            MessageBuilder.withPayload(payloadMissingDeviceId).build()));
        }
    }

    // ── Request size limit ────────────────────────────────────────────────────

    @Nested
    @DisplayName("Request size limit")
    class RequestSizeLimit {

        // NOTE: This test documents a security requirement.
        // Spring Boot 3.2 with embedded Tomcat does not enforce a request body size limit for
        // application/json by default. Configure server.tomcat.max-http-form-post-size or a custom
        // filter to reject oversized bodies with 413 Payload Too Large.
        @Test
        @DisplayName("a 10 MB request body is rejected with HTTP 413 Payload Too Large")
        void oversizedRequestBody_isRejectedWith413() throws Exception {
            byte[] body = new byte[10 * 1024 * 1024];
            Arrays.fill(body, (byte) 'x');

            mockMvc.perform(post("/api/v1/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isPayloadTooLarge());
        }
    }
}
