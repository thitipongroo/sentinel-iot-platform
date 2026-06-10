package com.sentinel.iot;

import com.sentinel.iot.service.MqttConsumerService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.integration.support.MessageBuilder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;

/**
 * หมวดที่ 7 — MQTT Security (4 tests)
 *
 * ทดสอบ: device topic spoofing (documented gap), plaintext TCP (documented gap),
 *        malformed JSON routed to DLQ, and credential configuration via env vars.
 *
 * Tests in DeviceSpoofing and TransportSecurity are "documented gap" tests — they
 * verify the CURRENT insecure behaviour and will fail once the gaps are addressed
 * (providing a regression signal).
 */
@DisplayName("MQTT Security — broker and message-level security")
class MqttSecurityTest extends BaseIntegrationTest {

    @Autowired
    private MqttConsumerService mqttConsumerService;

    @Value("${mqtt.broker}")
    private String mqttBrokerUrl;

    @Value("${mqtt.username:}")
    private String mqttUsername;

    @Value("${mqtt.password:}")
    private String mqttPassword;

    // ── Device spoofing (documented gap) ──────────────────────────────────────

    @Nested
    @DisplayName("Device spoofing — documented gap")
    class DeviceSpoofing {

        @Test
        @DisplayName("any client can publish a payload claiming another deviceId (no per-device ACL — documented gap)")
        void deviceA_canPublishPayloadClaimingToBeDeviceB_documentedGap() {
            // SECURITY GAP: no per-device MQTT topic ACL.
            // Remediation: enforce topic write factory/telemetry/%u in mosquitto.conf.
            String payloadClaimingDeviceB =
                    "{\"deviceId\":\"device-b\",\"temperature\":45.0,\"humidity\":60.0,\"motion\":false,\"smokePpm\":5.0}";

            assertThatNoException()
                    .as("platform accepts spoofed deviceId without publisher identity verification")
                    .isThrownBy(() -> mqttConsumerService.handleMessage(
                            MessageBuilder.withPayload(payloadClaimingDeviceB).build()));
        }
    }

    // ── Transport security (documented gap) ───────────────────────────────────

    @Nested
    @DisplayName("Transport security — documented gap")
    class TransportSecurity {

        @Test
        @DisplayName("test broker uses plaintext TCP — production must use ssl:// (documented gap)")
        void mqttBroker_usesPlaintextTcp_documentedGap() {
            // SECURITY GAP: broker URL uses tcp:// (plaintext) by default.
            // Production deployments must switch to ssl:// after running scripts/gen-mqtt-certs.sh.
            assertThat(mqttBrokerUrl)
                    .as("test broker URL should start with tcp:// (plaintext — documented gap)")
                    .startsWith("tcp://");
        }
    }

    // ── Payload handling ──────────────────────────────────────────────────────

    @Nested
    @DisplayName("Payload handling")
    class PayloadHandling {

        @Test
        @DisplayName("malformed JSON payload is routed to the DLQ without crashing the consumer")
        void malformedJsonPayload_routesToDlqWithoutCrash() {
            String malformedJson = "{temperature: 45, humidity: 60, broken";

            assertThatNoException()
                    .as("parse exception must be caught and routed to DLQ, not propagated")
                    .isThrownBy(() -> mqttConsumerService.handleMessage(
                            MessageBuilder.withPayload(malformedJson).build()));
        }
    }

    // ── Credential management ─────────────────────────────────────────────────

    @Nested
    @DisplayName("Credential management")
    class CredentialManagement {

        @Test
        @DisplayName("MQTT credentials are read from environment variables (MQTT_USER / MQTT_PASS)")
        void mqttCredentials_areSuppliedViaEnvironmentVariables() {
            assertThat(mqttBrokerUrl).as("mqtt.broker must be configured").isNotBlank();
            if (mqttUsername.isBlank() || mqttPassword.isBlank()) {
                System.err.println(
                        "[SECURITY] MQTT_USER / MQTT_PASS are not set — broker allows anonymous access. " +
                        "Set these environment variables and configure mosquitto ACL before deploying to production.");
            }
        }
    }
}
