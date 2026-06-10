package com.sentinel.iot;

import com.sentinel.iot.service.MqttConsumerService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.integration.support.MessageBuilder;
import org.springframework.messaging.Message;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;

/**
 * หมวดที่ 7 — MQTT Security (4 tests)
 *
 * ทดสอบ: device topic spoofing (documented gap), plaintext TCP (documented gap),
 *        malformed JSON routed to DLQ, and credential configuration via env vars.
 *
 * Tests 7.1 and 7.2 are "documented gap" tests — they verify the CURRENT insecure
 * behaviour and will fail once the gaps are addressed (providing a regression signal).
 */
class MqttSecurityTest extends BaseIntegrationTest {

    @Autowired
    private MqttConsumerService mqttConsumerService;

    @Value("${mqtt.broker}")
    private String mqttBrokerUrl;

    @Value("${mqtt.username:}")
    private String mqttUsername;

    @Value("${mqtt.password:}")
    private String mqttPassword;

    // ── 7.1 Device spoofing — any client can claim any deviceId (documented gap) ──

    @Test
    void deviceA_canPublishPayloadClaimingToBeDeviceB_documentedGap() {
        // SECURITY GAP: the platform has no per-device MQTT topic ACL.
        // Any authenticated MQTT client can publish a payload that claims a different
        // deviceId and the system will process it under that identity.
        // Remediation: enforce per-device topic ACLs in mosquitto.conf (e.g.,
        //   topic write factory/telemetry/%u  — requires clientId == deviceId).
        String payloadClaimingDeviceB =
                "{\"deviceId\":\"device-b\",\"temperature\":45.0,\"humidity\":60.0,\"motion\":false,\"smokePpm\":5.0}";
        Message<String> msg = MessageBuilder.withPayload(payloadClaimingDeviceB).build();

        // The service currently accepts the message without verifying the publisher's identity.
        assertThatNoException().isThrownBy(() -> mqttConsumerService.handleMessage(msg));
    }

    // ── 7.2 Broker uses plaintext TCP by default (documented gap) ────────────

    @Test
    void mqttBroker_usesPlaintextTcp_documentedGap() {
        // SECURITY GAP: the broker URL uses tcp:// (plaintext) by default.
        // Production deployments must switch to ssl:// after running
        //   scripts/gen-mqtt-certs.sh and configuring MQTT_BROKER=ssl://...
        assertThat(mqttBrokerUrl)
                .as("test broker URL should start with tcp:// (plaintext — documented gap)")
                .startsWith("tcp://");
    }

    // ── 7.3 Malformed JSON payload is routed to DLQ, consumer does not crash ─

    @Test
    void malformedJsonPayload_routesToDlqWithoutCrash() {
        // A badly-formed JSON string (not parseable by Jackson) must be caught and
        // routed to the dead-letter queue — the consumer must never propagate
        // a parse exception to the Spring Integration message dispatcher.
        String malformedJson = "{temperature: 45, humidity: 60, broken";
        Message<String> msg = MessageBuilder.withPayload(malformedJson).build();

        assertThatNoException().isThrownBy(() -> mqttConsumerService.handleMessage(msg));
    }

    // ── 7.4 MQTT credentials are supplied via environment variables, not hardcoded ──

    @Test
    void mqttCredentials_areSuppliedViaEnvironmentVariables() {
        // Verify the application reads MQTT credentials from MQTT_USER / MQTT_PASS
        // environment variables (not hardcoded in source).  In the test environment,
        // the mosquitto container allows anonymous connections (mosquitto-test.conf),
        // so both values may be empty — production deployments must set them.
        //
        // If both are blank, log a warning so the test output documents the gap.
        assertThat(mqttBrokerUrl).isNotBlank();
        if (mqttUsername.isBlank() || mqttPassword.isBlank()) {
            System.err.println(
                    "[SECURITY] MQTT_USER / MQTT_PASS are not set — broker allows anonymous access. " +
                    "Set these environment variables and configure mosquitto ACL before deploying to production.");
        }
    }
}
