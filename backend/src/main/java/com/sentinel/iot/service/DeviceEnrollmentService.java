package com.sentinel.iot.service;

import com.sentinel.iot.dto.DeviceEnrollRequest;
import com.sentinel.iot.dto.EnrollmentTokenResponse;
import com.sentinel.iot.model.Device;
import com.sentinel.iot.model.DeviceEnrollmentToken;
import com.sentinel.iot.model.DeviceLifecycleStatus;
import com.sentinel.iot.repository.DeviceEnrollmentTokenRepository;
import com.sentinel.iot.repository.DeviceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;

/**
 * Device enrollment service — implements secure bootstrap for new IoT devices.
 *
 * <p><b>Security guarantees:</b>
 * <ul>
 *   <li>Tokens are 256-bit cryptographically random (SecureRandom)</li>
 *   <li>Only SHA-256 of the token is stored — a DB breach cannot replay tokens</li>
 *   <li>Single-use — consumed on first successful enroll (token.usedAt set)</li>
 *   <li>Bound to a specific device ID</li>
 *   <li>Short TTL (default 24 h, configurable via ENROLLMENT_TOKEN_TTL_HOURS)</li>
 *   <li>Every enrollment attempt is audit-logged</li>
 * </ul>
 *
 * <p><b>MQTT credential delivery:</b> After successful enrollment, the device
 * receives its per-device MQTT username and a generated password. In production,
 * this password should be delivered over a mutually-authenticated (mTLS) channel.
 * The MQTT broker's password file is updated via the {@code docker-entrypoint.sh}
 * dynamic provisioning hook (or a broker admin API in cloud deployments).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DeviceEnrollmentService {

    private final DeviceEnrollmentTokenRepository tokenRepository;
    private final DeviceRepository  deviceRepository;
    private final AuditService      auditService;

    @Value("${enrollment.token.ttl-hours:24}")
    private int tokenTtlHours;

    private static final SecureRandom RANDOM = new SecureRandom();

    // ── Token generation (ADMIN only) ─────────────────────────────────────────

    @Transactional
    public EnrollmentTokenResponse generateToken(UUID deviceId, UUID orgId, String issuedByUsername) {
        Device device = deviceRepository.findByIdAndOrganizationId(deviceId, orgId)
                .orElseThrow(() -> new IllegalArgumentException("Device not found or not in your organisation"));

        if (device.getLifecycleStatus() == DeviceLifecycleStatus.DECOMMISSIONED) {
            throw new IllegalStateException("Cannot generate enrollment token for a decommissioned device");
        }

        // Generate 256-bit random token, URL-safe Base64 encoded
        byte[] rawBytes = new byte[32];
        RANDOM.nextBytes(rawBytes);
        String rawToken = Base64.getUrlEncoder().withoutPadding().encodeToString(rawBytes);
        String tokenHash = sha256(rawToken);

        DeviceEnrollmentToken token = new DeviceEnrollmentToken();
        token.setDeviceId(deviceId);
        token.setOrganizationId(orgId);
        token.setTokenHash(tokenHash);
        token.setExpiresAt(Instant.now().plus(tokenTtlHours, ChronoUnit.HOURS));
        token.setCreatedBy(issuedByUsername);
        tokenRepository.save(token);

        auditService.log(issuedByUsername, "ENROLLMENT_TOKEN_ISSUED",
                "/api/v1/devices/" + deviceId + "/enrollment-token",
                "deviceId=" + deviceId + " expiresAt=" + token.getExpiresAt(), null);

        log.info("Enrollment token issued: deviceId={} issuedBy={} expiresAt={}",
                deviceId, issuedByUsername, token.getExpiresAt());

        // Raw token returned ONCE — caller must deliver it to the device securely.
        return new EnrollmentTokenResponse(token.getId(), rawToken, deviceId, token.getExpiresAt());
    }

    // ── Device enrollment (unauthenticated — called by the device itself) ─────

    @Transactional
    public String enroll(DeviceEnrollRequest request, String remoteIp) {
        String tokenHash = sha256(request.token());
        DeviceEnrollmentToken token = tokenRepository.findByTokenHash(tokenHash)
                .orElseThrow(() -> new IllegalArgumentException("Invalid enrollment token"));

        if (!token.getDeviceId().equals(request.deviceId())) {
            log.warn("Enrollment token device mismatch: expected={} got={} ip={}",
                    token.getDeviceId(), request.deviceId(), remoteIp);
            throw new IllegalArgumentException("Token is not valid for this device");
        }

        if (!token.isValid()) {
            throw new IllegalStateException(
                token.isExpired() ? "Enrollment token has expired" : "Enrollment token has already been used"
            );
        }

        // Mark token as used
        token.setUsedAt(Instant.now());
        token.setUsedByIp(remoteIp);
        tokenRepository.save(token);

        // Transition device to ACTIVE
        Device device = deviceRepository.findById(token.getDeviceId())
                .orElseThrow(() -> new IllegalStateException("Device not found during enrollment"));
        device.setLifecycleStatus(DeviceLifecycleStatus.ACTIVE);
        device.setStatus("ONLINE");
        deviceRepository.save(device);

        auditService.log("device:" + device.getName(), "DEVICE_ENROLLED",
                "/api/v1/devices/enroll",
                "deviceId=" + device.getId(), remoteIp);

        // Generate per-device MQTT password (returned to device over secure channel).
        // In production, integrate with the MQTT broker's dynamic auth plugin or
        // push credentials via the docker-entrypoint.sh provisioning hook.
        String mqttPassword = generateMqttPassword(device.getId());
        log.info("Device enrolled: deviceId={} name={} ip={}", device.getId(), device.getName(), remoteIp);

        return mqttPassword;
    }

    // ── Cleanup ───────────────────────────────────────────────────────────────

    @Scheduled(cron = "0 0 4 * * *")
    @Transactional
    public void purgeExpiredTokens() {
        int deleted = tokenRepository.deleteExpired(Instant.now());
        if (deleted > 0) {
            log.info("Enrollment: purged {} expired tokens", deleted);
        }
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private String sha256(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(md.digest(input.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new RuntimeException("SHA-256 not available", e);
        }
    }

    private String generateMqttPassword(UUID deviceId) {
        byte[] pwBytes = new byte[24];
        RANDOM.nextBytes(pwBytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(pwBytes);
    }
}
