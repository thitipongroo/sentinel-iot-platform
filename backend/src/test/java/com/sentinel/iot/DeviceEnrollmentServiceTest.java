package com.sentinel.iot;

import com.sentinel.iot.dto.DeviceEnrollRequest;
import com.sentinel.iot.dto.EnrollmentTokenResponse;
import com.sentinel.iot.model.Device;
import com.sentinel.iot.model.DeviceEnrollmentToken;
import com.sentinel.iot.model.DeviceLifecycleStatus;
import com.sentinel.iot.repository.DeviceEnrollmentTokenRepository;
import com.sentinel.iot.repository.DeviceRepository;
import com.sentinel.iot.service.AuditService;
import com.sentinel.iot.service.DeviceEnrollmentService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@Tag("unit")
@DisplayName("DeviceEnrollmentService")
@ExtendWith(MockitoExtension.class)
class DeviceEnrollmentServiceTest {

    @Mock DeviceEnrollmentTokenRepository tokenRepository;
    @Mock DeviceRepository                 deviceRepository;
    @Mock AuditService                     auditService;

    @InjectMocks DeviceEnrollmentService service;

    private final UUID orgId    = UUID.fromString("aaaaaaaa-0000-0000-0000-000000000001");
    private final UUID deviceId = UUID.fromString("dddddddd-0000-0000-0000-000000000001");

    @SuppressWarnings("null")
    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(service, "tokenTtlHours", 24);
    }

    // ── generateToken ─────────────────────────────────────────────────────────

    @Nested
    @DisplayName("generateToken")
    class GenerateToken {

        @SuppressWarnings("null")
        @Test
        @DisplayName("stores the SHA-256 hash of the raw token, not the raw token itself")
        void generateToken_storedHashIsShA256OfRawToken() throws Exception {
            when(deviceRepository.findByIdAndOrganizationId(deviceId, orgId))
                    .thenReturn(Optional.of(activeDevice()));
            ArgumentCaptor<DeviceEnrollmentToken> captor = ArgumentCaptor.forClass(DeviceEnrollmentToken.class);
            when(tokenRepository.save(captor.capture())).thenAnswer(inv -> inv.getArgument(0));

            EnrollmentTokenResponse response = service.generateToken(deviceId, orgId, "admin");

            String expectedHash = sha256(response.token());
            assertThat(captor.getValue().getTokenHash())
                    .as("persisted hash must equal SHA-256 of the returned raw token")
                    .isEqualTo(expectedHash);
            assertThat(captor.getValue().getTokenHash())
                    .as("persisted hash must not equal the raw token")
                    .isNotEqualTo(response.token());
        }

        @SuppressWarnings("null")
        @Test
        @DisplayName("returns a 43-character URL-safe Base64 token (32 bytes, no padding)")
        void generateToken_rawTokenIsUrlSafeBase64_43Chars() {
            when(deviceRepository.findByIdAndOrganizationId(deviceId, orgId))
                    .thenReturn(Optional.of(activeDevice()));
            when(tokenRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            EnrollmentTokenResponse response = service.generateToken(deviceId, orgId, "admin");

            assertThat(response.token())
                    .as("32 bytes → Base64url without padding = 43 chars")
                    .hasSize(43)
                    .matches("[A-Za-z0-9_-]+");
        }

        @SuppressWarnings("null")
        @Test
        @DisplayName("sets expiresAt to exactly tokenTtlHours from now (±1 hour tolerance)")
        void generateToken_setsExpiresAtTtlHoursFromNow() {
            when(deviceRepository.findByIdAndOrganizationId(deviceId, orgId))
                    .thenReturn(Optional.of(activeDevice()));
            ArgumentCaptor<DeviceEnrollmentToken> captor = ArgumentCaptor.forClass(DeviceEnrollmentToken.class);
            when(tokenRepository.save(captor.capture())).thenAnswer(inv -> inv.getArgument(0));

            service.generateToken(deviceId, orgId, "admin");

            Instant expiresAt = captor.getValue().getExpiresAt();
            assertThat(expiresAt)
                    .as("expiresAt must be within ±1 hour of 24 hours from now")
                    .isBetween(
                            Instant.now().plus(23, ChronoUnit.HOURS),
                            Instant.now().plus(25, ChronoUnit.HOURS));
        }

        @SuppressWarnings("null")
        @Test
        @DisplayName("logs an ENROLLMENT_TOKEN_ISSUED audit entry with the requesting user")
        void generateToken_logsAuditEntry() {
            when(deviceRepository.findByIdAndOrganizationId(deviceId, orgId))
                    .thenReturn(Optional.of(activeDevice()));
            when(tokenRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            service.generateToken(deviceId, orgId, "admin-user");

            verify(auditService).log(eq("admin-user"), eq("ENROLLMENT_TOKEN_ISSUED"), any(), any(), any());
        }

        @Test
        @DisplayName("throws IllegalArgumentException when the device does not exist")
        void generateToken_throwsWhenDeviceNotFound() {
            when(deviceRepository.findByIdAndOrganizationId(deviceId, orgId))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.generateToken(deviceId, orgId, "admin"))
                    .as("non-existent device")
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("not found");
        }

        @Test
        @DisplayName("throws IllegalStateException when the device is DECOMMISSIONED")
        void generateToken_throwsWhenDeviceIsDecommissioned() {
            Device device = new Device();
            device.setId(deviceId);
            device.setLifecycleStatus(DeviceLifecycleStatus.DECOMMISSIONED);
            when(deviceRepository.findByIdAndOrganizationId(deviceId, orgId))
                    .thenReturn(Optional.of(device));

            assertThatThrownBy(() -> service.generateToken(deviceId, orgId, "admin"))
                    .as("decommissioned device")
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("decommissioned");
        }
    }

    // ── enroll ────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("enroll")
    class Enroll {

        @SuppressWarnings("null")
        @Test
        @DisplayName("activates device, marks token used, and returns an MQTT password on success")
        void enroll_success_activatesDeviceAndMarksTokenUsed() throws Exception {
            String rawToken = "test-enrollment-token-for-unit-test";
            DeviceEnrollmentToken token = validToken(rawToken);
            when(tokenRepository.findByTokenHash(sha256(rawToken))).thenReturn(Optional.of(token));
            when(tokenRepository.save(token)).thenReturn(token);

            Device device = activeDevice();
            when(deviceRepository.findById(deviceId)).thenReturn(Optional.of(device));
            when(deviceRepository.save(device)).thenReturn(device);

            String mqttPassword = service.enroll(new DeviceEnrollRequest(deviceId, rawToken, null), "10.0.0.1");

            assertThat(mqttPassword).as("returned MQTT password").isNotBlank();
            assertThat(device.getLifecycleStatus()).as("lifecycle status after enroll").isEqualTo(DeviceLifecycleStatus.ACTIVE);
            assertThat(device.getStatus()).as("device status after enroll").isEqualTo("ONLINE");
            assertThat(token.getUsedAt()).as("token usedAt timestamp").isNotNull();
            assertThat(token.getUsedByIp()).as("token usedByIp").isEqualTo("10.0.0.1");
        }

        @SuppressWarnings("null")
        @Test
        @DisplayName("returned MQTT password is a URL-safe Base64 string")
        void enroll_returnsMqttPassword_urlSafeBase64() throws Exception {
            String rawToken = "test-enroll-token-abc";
            DeviceEnrollmentToken token = validToken(rawToken);
            when(tokenRepository.findByTokenHash(sha256(rawToken))).thenReturn(Optional.of(token));
            when(tokenRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(deviceRepository.findById(deviceId)).thenReturn(Optional.of(activeDevice()));
            when(deviceRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            String mqttPassword = service.enroll(new DeviceEnrollRequest(deviceId, rawToken, null), "10.0.0.1");

            assertThat(mqttPassword)
                    .as("MQTT password must be non-blank URL-safe Base64")
                    .isNotBlank()
                    .matches("[A-Za-z0-9_-]+");
        }

        @Test
        @DisplayName("throws IllegalArgumentException when the token hash is not found in the store")
        void enroll_throwsForUnknownToken() {
            when(tokenRepository.findByTokenHash(any())).thenReturn(Optional.empty());

            assertThatThrownBy(() ->
                    service.enroll(new DeviceEnrollRequest(deviceId, "bad-token", null), "10.0.0.1"))
                    .as("unknown token")
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Invalid");
        }

        @SuppressWarnings("null")
        @Test
        @DisplayName("throws IllegalArgumentException when the token belongs to a different device")
        void enroll_throwsForDeviceMismatch() throws Exception {
            String rawToken = "token-xyz";
            DeviceEnrollmentToken token = validToken(rawToken);
            token.setDeviceId(UUID.randomUUID()); // token issued for a different device
            when(tokenRepository.findByTokenHash(sha256(rawToken))).thenReturn(Optional.of(token));

            assertThatThrownBy(() ->
                    service.enroll(new DeviceEnrollRequest(deviceId, rawToken, null), "10.0.0.1"))
                    .as("token/device mismatch")
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("not valid for this device");
        }

        @SuppressWarnings("null")
        @Test
        @DisplayName("throws IllegalStateException when the token has passed its expiry time")
        void enroll_throwsForExpiredToken() throws Exception {
            String rawToken = "expired-token";
            DeviceEnrollmentToken token = validToken(rawToken);
            token.setExpiresAt(Instant.now().minusSeconds(1));
            when(tokenRepository.findByTokenHash(sha256(rawToken))).thenReturn(Optional.of(token));

            assertThatThrownBy(() ->
                    service.enroll(new DeviceEnrollRequest(deviceId, rawToken, null), "10.0.0.1"))
                    .as("expired token")
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("expired");
        }

        @SuppressWarnings("null")
        @Test
        @DisplayName("throws IllegalStateException when the token has already been consumed")
        void enroll_throwsForAlreadyUsedToken() throws Exception {
            String rawToken = "used-token";
            DeviceEnrollmentToken token = validToken(rawToken);
            token.setUsedAt(Instant.now().minusSeconds(60));
            when(tokenRepository.findByTokenHash(sha256(rawToken))).thenReturn(Optional.of(token));

            assertThatThrownBy(() ->
                    service.enroll(new DeviceEnrollRequest(deviceId, rawToken, null), "10.0.0.1"))
                    .as("already-used token")
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("already been used");
        }
    }

    // ── purgeExpiredTokens ────────────────────────────────────────────────────

    @Nested
    @DisplayName("purgeExpiredTokens")
    class PurgeExpiredTokens {

        @SuppressWarnings("null")
        @Test
        @DisplayName("delegates to tokenRepository.deleteExpired with the current timestamp")
        void purgeExpiredTokens_callsDeleteExpiredWithCurrentTime() {
            Instant before = Instant.now();
            service.purgeExpiredTokens();
            ArgumentCaptor<Instant> captor = ArgumentCaptor.forClass(Instant.class);
            verify(tokenRepository).deleteExpired(captor.capture());
            assertThat(captor.getValue())
                    .as("cutoff instant must be approximately now")
                    .isBetween(before.minusSeconds(5), Instant.now().plusSeconds(5));
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private Device activeDevice() {
        Device d = new Device();
        d.setId(deviceId);
        d.setName("sensor-1");
        d.setLifecycleStatus(DeviceLifecycleStatus.ACTIVE);
        d.setOrganizationId(orgId);
        return d;
    }

    private DeviceEnrollmentToken validToken(String rawToken) throws Exception {
        DeviceEnrollmentToken token = new DeviceEnrollmentToken();
        token.setDeviceId(deviceId);
        token.setOrganizationId(orgId);
        token.setTokenHash(sha256(rawToken));
        token.setExpiresAt(Instant.now().plusSeconds(3600));
        token.setCreatedBy("admin");
        return token;
    }

    private static String sha256(String input) throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        return HexFormat.of().formatHex(md.digest(input.getBytes(StandardCharsets.UTF_8)));
    }
}
