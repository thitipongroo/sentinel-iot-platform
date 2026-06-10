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

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DeviceEnrollmentServiceTest {

    @Mock DeviceEnrollmentTokenRepository tokenRepository;
    @Mock DeviceRepository deviceRepository;
    @Mock AuditService auditService;

    @InjectMocks DeviceEnrollmentService service;

    private final UUID orgId    = UUID.fromString("aaaaaaaa-0000-0000-0000-000000000001");
    private final UUID deviceId = UUID.fromString("dddddddd-0000-0000-0000-000000000001");

    @SuppressWarnings("null")
    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(service, "tokenTtlHours", 24);
    }

    // ---- generateToken -----------------------------------------------------

    @SuppressWarnings("null")
    @Test
    void generateToken_storedHashIsShA256OfRawToken() throws Exception {
        Device device = activeDevice();
        when(deviceRepository.findByIdAndOrganizationId(deviceId, orgId)).thenReturn(Optional.of(device));
        ArgumentCaptor<DeviceEnrollmentToken> captor = ArgumentCaptor.forClass(DeviceEnrollmentToken.class);
        when(tokenRepository.save(captor.capture())).thenAnswer(inv -> inv.getArgument(0));

        EnrollmentTokenResponse response = service.generateToken(deviceId, orgId, "admin");

        String expectedHash = sha256(response.token());
        assertThat(captor.getValue().getTokenHash()).isEqualTo(expectedHash);
        assertThat(captor.getValue().getTokenHash()).isNotEqualTo(response.token()); // hash != raw
    }

    @SuppressWarnings("null")
    @Test
    void generateToken_rawTokenIsUrlSafeBase64_43Chars() {
        Device device = activeDevice();
        when(deviceRepository.findByIdAndOrganizationId(deviceId, orgId)).thenReturn(Optional.of(device));
        when(tokenRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        EnrollmentTokenResponse response = service.generateToken(deviceId, orgId, "admin");

        // 32 bytes → Base64url without padding = 43 chars
        assertThat(response.token()).hasSize(43).matches("[A-Za-z0-9_-]+");
    }

    @SuppressWarnings("null")
    @Test
    void generateToken_setsExpiresAtTtlHoursFromNow() {
        Device device = activeDevice();
        when(deviceRepository.findByIdAndOrganizationId(deviceId, orgId)).thenReturn(Optional.of(device));
        ArgumentCaptor<DeviceEnrollmentToken> captor = ArgumentCaptor.forClass(DeviceEnrollmentToken.class);
        when(tokenRepository.save(captor.capture())).thenAnswer(inv -> inv.getArgument(0));

        service.generateToken(deviceId, orgId, "admin");

        Instant expiresAt = captor.getValue().getExpiresAt();
        assertThat(expiresAt).isBetween(
                Instant.now().plus(23, ChronoUnit.HOURS),
                Instant.now().plus(25, ChronoUnit.HOURS));
    }

    @SuppressWarnings("null")
    @Test
    void generateToken_logsAuditEntry() {
        Device device = activeDevice();
        when(deviceRepository.findByIdAndOrganizationId(deviceId, orgId)).thenReturn(Optional.of(device));
        when(tokenRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.generateToken(deviceId, orgId, "admin-user");

        verify(auditService).log(eq("admin-user"), eq("ENROLLMENT_TOKEN_ISSUED"), any(), any(), any());
    }

    @SuppressWarnings("null")
    @Test
    void generateToken_throwsWhenDeviceNotFound() {
        when(deviceRepository.findByIdAndOrganizationId(deviceId, orgId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.generateToken(deviceId, orgId, "admin"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not found");
    }

    @SuppressWarnings("null")
    @Test
    void generateToken_throwsWhenDeviceIsDecommissioned() {
        Device device = new Device();
        device.setId(deviceId);
        device.setLifecycleStatus(DeviceLifecycleStatus.DECOMMISSIONED);
        when(deviceRepository.findByIdAndOrganizationId(deviceId, orgId)).thenReturn(Optional.of(device));

        assertThatThrownBy(() -> service.generateToken(deviceId, orgId, "admin"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("decommissioned");
    }

    // ---- enroll ------------------------------------------------------------

    @SuppressWarnings("null")
    @Test
    void enroll_success_activatesDeviceAndMarksTokenUsed() throws Exception {
        String rawToken = "test-enrollment-token-for-unit-test";
        DeviceEnrollmentToken token = validToken(rawToken);
        when(tokenRepository.findByTokenHash(sha256(rawToken))).thenReturn(Optional.of(token));
        when(tokenRepository.save(token)).thenReturn(token);

        Device device = activeDevice();
        when(deviceRepository.findById(deviceId)).thenReturn(Optional.of(device));
        when(deviceRepository.save(device)).thenReturn(device);

        String mqttPassword = service.enroll(new DeviceEnrollRequest(deviceId, rawToken, null), "10.0.0.1");

        assertThat(mqttPassword).isNotBlank();
        assertThat(device.getLifecycleStatus()).isEqualTo(DeviceLifecycleStatus.ACTIVE);
        assertThat(device.getStatus()).isEqualTo("ONLINE");
        assertThat(token.getUsedAt()).isNotNull();
        assertThat(token.getUsedByIp()).isEqualTo("10.0.0.1");
    }

    @SuppressWarnings("null")
    @Test
    void enroll_returnsMqttPassword_urlSafeBase64() throws Exception {
        String rawToken = "test-enroll-token-abc";
        DeviceEnrollmentToken token = validToken(rawToken);
        when(tokenRepository.findByTokenHash(sha256(rawToken))).thenReturn(Optional.of(token));
        when(tokenRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(deviceRepository.findById(deviceId)).thenReturn(Optional.of(activeDevice()));
        when(deviceRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        String mqttPassword = service.enroll(new DeviceEnrollRequest(deviceId, rawToken, null), "10.0.0.1");

        assertThat(mqttPassword).isNotBlank().matches("[A-Za-z0-9_-]+");
    }

    @SuppressWarnings("null")
    @Test
    void enroll_throwsForUnknownToken() {
        when(tokenRepository.findByTokenHash(any())).thenReturn(Optional.empty());

        assertThatThrownBy(() ->
                service.enroll(new DeviceEnrollRequest(deviceId, "bad-token", null), "10.0.0.1"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid");
    }

    @SuppressWarnings("null")
    @Test
    void enroll_throwsForDeviceMismatch() throws Exception {
        String rawToken = "token-xyz";
        UUID otherDevice = UUID.randomUUID();
        DeviceEnrollmentToken token = validToken(rawToken);
        token.setDeviceId(otherDevice); // token belongs to a different device
        when(tokenRepository.findByTokenHash(sha256(rawToken))).thenReturn(Optional.of(token));

        assertThatThrownBy(() ->
                service.enroll(new DeviceEnrollRequest(deviceId, rawToken, null), "10.0.0.1"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not valid for this device");
    }

    @SuppressWarnings("null")
    @Test
    void enroll_throwsForExpiredToken() throws Exception {
        String rawToken = "expired-token";
        DeviceEnrollmentToken token = validToken(rawToken);
        token.setExpiresAt(Instant.now().minusSeconds(1)); // expired
        when(tokenRepository.findByTokenHash(sha256(rawToken))).thenReturn(Optional.of(token));

        assertThatThrownBy(() ->
                service.enroll(new DeviceEnrollRequest(deviceId, rawToken, null), "10.0.0.1"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("expired");
    }

    @SuppressWarnings("null")
    @Test
    void enroll_throwsForAlreadyUsedToken() throws Exception {
        String rawToken = "used-token";
        DeviceEnrollmentToken token = validToken(rawToken);
        token.setUsedAt(Instant.now().minusSeconds(60)); // already used
        when(tokenRepository.findByTokenHash(sha256(rawToken))).thenReturn(Optional.of(token));

        assertThatThrownBy(() ->
                service.enroll(new DeviceEnrollRequest(deviceId, rawToken, null), "10.0.0.1"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("already been used");
    }

    // ---- purgeExpiredTokens ------------------------------------------------

    @SuppressWarnings("null")
    @Test
    void purgeExpiredTokens_callsDeleteExpiredWithCurrentTime() {
        Instant before = Instant.now();
        service.purgeExpiredTokens();
        ArgumentCaptor<Instant> captor = ArgumentCaptor.forClass(Instant.class);
        verify(tokenRepository).deleteExpired(captor.capture());
        assertThat(captor.getValue()).isBetween(before.minusSeconds(5), Instant.now().plusSeconds(5));
    }

    // ---- Helpers -----------------------------------------------------------

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
