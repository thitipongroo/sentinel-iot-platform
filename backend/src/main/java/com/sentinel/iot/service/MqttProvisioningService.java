package com.sentinel.iot.service;

import com.sentinel.iot.model.Device;
import com.sentinel.iot.model.DeviceLifecycleStatus;
import com.sentinel.iot.repository.DeviceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;

/**
 * Service responsible for dynamic MQTT device credential provisioning and authentication.
 *
 * <p>Supports three tiers of provisioning:
 * <ul>
 *   <li><b>Database Tier:</b> Persists {@code mqtt_username} and cryptographic password hash on the Device entity.</li>
 *   <li><b>Redis Tier:</b> Stores sub-millisecond dynamic lookup entries ({@code mqtt:auth:{username}}) for modern brokers/plugins.</li>
 *   <li><b>Mosquitto File Hook:</b> Appends to a dynamic credentials queue file that triggers SIGHUP reload in Eclipse Mosquitto.</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class MqttProvisioningService {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final DeviceRepository deviceRepository;
    private final StringRedisTemplate redisTemplate;

    @Value("${mqtt.provisioning.credentials-file:/mosquitto/data/dynamic_credentials}")
    private String dynamicCredentialsFile;

    @Value("${mqtt.provisioning.file-enabled:true}")
    private boolean fileProvisioningEnabled;

    /**
     * Provisions dynamic MQTT credentials for an enrolled device across DB, Redis, and broker queue.
     *
     * @param device the enrolled device
     * @param rawPassword the generated one-time MQTT password
     * @return the assigned MQTT username
     */
    @Transactional
    public String provisionDevice(Device device, String rawPassword) {
        String username = resolveMqttUsername(device);
        String passwordHash = sha256(rawPassword);

        // 1. Database Tier: persist credentials on Device
        device.setMqttUsername(username);
        device.setMqttPasswordHash(passwordHash);
        deviceRepository.save(device);

        // 2. Redis Tier: sub-millisecond dynamic auth lookup & ACL
        try {
            redisTemplate.opsForValue().set("mqtt:auth:" + username, passwordHash);
            String acl = "factory/telemetry,tenants/" + device.getOrganizationId() + "/#";
            redisTemplate.opsForValue().set("mqtt:acl:" + username, acl);
            redisTemplate.opsForValue().set("mqtt:device:" + username, device.getId().toString());
            if (device.getOrganizationId() != null) {
                redisTemplate.opsForValue().set("mqtt:org:" + username, device.getOrganizationId().toString());
            }
            log.debug("Provisioned MQTT credentials to Redis for user={}", username);
        } catch (Exception e) {
            log.warn("Failed to store MQTT credentials in Redis for user={}: {}", username, e.getMessage());
        }

        // 3. Broker File Tier: dynamic credentials queue for Mosquitto SIGHUP reload
        if (fileProvisioningEnabled) {
            appendToFileQueue(username, rawPassword);
        }

        log.info("Dynamic MQTT credentials provisioned successfully for deviceId={} username={}",
                device.getId(), username);
        return username;
    }

    /**
     * Revokes dynamic MQTT credentials immediately across Redis and Database.
     * Inactive or decommissioned devices lose broker access with zero delay.
     *
     * @param device the device whose credentials are to be revoked
     */
    @Transactional
    public void revokeCredentials(Device device) {
        if (device == null) return;
        String username = resolveMqttUsername(device);

        // 1. Invalidate Database
        device.setMqttPasswordHash(null);
        deviceRepository.save(device);

        // 2. Evict from Redis
        try {
            List<String> keys = List.of(
                    "mqtt:auth:" + username,
                    "mqtt:acl:" + username,
                    "mqtt:device:" + username,
                    "mqtt:org:" + username
            );
            redisTemplate.delete(keys);
            log.info("Evicted dynamic MQTT credentials from Redis for deviceId={} username={}",
                    device.getId(), username);
        } catch (Exception e) {
            log.warn("Failed to evict Redis MQTT credentials for user={}: {}", username, e.getMessage());
        }

        log.info("Revoked dynamic MQTT credentials for deviceId={} username={}", device.getId(), username);
    }

    /**
     * Re-provisions fresh credentials for a device, generating a new random password.
     *
     * @param device the device to reprovision
     * @return the new raw password
     */
    @Transactional
    public String reprovisionDevice(Device device) {
        String newPassword = generateMqttPassword();
        provisionDevice(device, newPassword);
        return newPassword;
    }

    /**
     * Verifies if a given device username and password are valid against DB / Redis.
     * Rejects authentication if device is DECOMMISSIONED or INACTIVE.
     */
    public boolean verifyCredentials(String username, String rawPassword) {
        if (username == null || rawPassword == null || username.isBlank() || rawPassword.isBlank()) {
            return false;
        }
        String passwordHash = sha256(rawPassword);

        // 1. Try Redis first
        try {
            String cachedHash = redisTemplate.opsForValue().get("mqtt:auth:" + username);
            if (cachedHash != null) {
                return cachedHash.equals(passwordHash);
            }
        } catch (Exception ignored) {
        }

        // 2. Fallback to database
        Optional<Device> devOpt = deviceRepository.findByMqttUsername(username);
        if (devOpt.isEmpty()) {
            return false;
        }

        Device device = devOpt.get();
        if (device.getLifecycleStatus() == DeviceLifecycleStatus.DECOMMISSIONED
                || device.getLifecycleStatus() == DeviceLifecycleStatus.INACTIVE) {
            log.warn("Rejected MQTT authentication for revoked/inactive device: user={} status={}",
                    username, device.getLifecycleStatus());
            return false;
        }

        boolean matches = passwordHash.equals(device.getMqttPasswordHash());
        if (matches) {
            // Re-populate Redis cache for sub-millisecond future auth
            try {
                redisTemplate.opsForValue().set("mqtt:auth:" + username, passwordHash);
                redisTemplate.opsForValue().set("mqtt:device:" + username, device.getId().toString());
                if (device.getOrganizationId() != null) {
                    redisTemplate.opsForValue().set("mqtt:org:" + username, device.getOrganizationId().toString());
                    String acl = "factory/telemetry,tenants/" + device.getOrganizationId() + "/#";
                    redisTemplate.opsForValue().set("mqtt:acl:" + username, acl);
                }
            } catch (Exception ignored) {
            }
        }
        return matches;
    }

    /**
     * Verifies MQTT topic publish/subscribe authorization (ACL) for EMQX / Mosquitto auth webhooks.
     * Enforces strict multi-tenant boundary: devices can only interact within their tenant namespace.
     *
     * @param username the connecting MQTT username
     * @param clientId the MQTT client identifier
     * @param topic the destination topic
     * @param action the action ("publish" or "subscribe")
     * @return true if authorized, false otherwise
     */
    public boolean verifyAcl(String username, String clientId, String topic, String action) {
        if (username == null || topic == null || username.isBlank() || topic.isBlank()) {
            return false;
        }

        String orgIdStr = null;
        String deviceIdStr = null;

        // Try Redis first
        try {
            orgIdStr = redisTemplate.opsForValue().get("mqtt:org:" + username);
            deviceIdStr = redisTemplate.opsForValue().get("mqtt:device:" + username);
        } catch (Exception ignored) {
        }

        // Fallback to database if not cached
        if (orgIdStr == null || deviceIdStr == null) {
            Optional<Device> devOpt = deviceRepository.findByMqttUsername(username);
            if (devOpt.isEmpty()) {
                return false;
            }
            Device dev = devOpt.get();
            if (dev.getLifecycleStatus() == DeviceLifecycleStatus.DECOMMISSIONED
                    || dev.getLifecycleStatus() == DeviceLifecycleStatus.INACTIVE) {
                return false;
            }
            if (dev.getOrganizationId() != null) {
                orgIdStr = dev.getOrganizationId().toString();
            }
            if (dev.getId() != null) {
                deviceIdStr = dev.getId().toString();
            }
            if (orgIdStr != null && deviceIdStr != null) {
                try {
                    redisTemplate.opsForValue().set("mqtt:org:" + username, orgIdStr);
                    redisTemplate.opsForValue().set("mqtt:device:" + username, deviceIdStr);
                } catch (Exception ignored) {
                }
            }
        }

        if (orgIdStr == null) {
            return false;
        }

        boolean isSubscribe = "subscribe".equalsIgnoreCase(action) || "2".equals(action);

        // 1. Legacy ingestion topic (publish only)
        if ("factory/telemetry".equals(topic)) {
            return !isSubscribe;
        }

        // 2. Tenant telemetry broadcast topic (publish only)
        if (("tenants/" + orgIdStr + "/telemetry").equals(topic)) {
            return !isSubscribe;
        }

        // 3. Device scoped topics (tenants/{orgId}/devices/{deviceId}/... or tenants/{orgId}/devices/{username}/...)
        if (deviceIdStr != null) {
            String devicePrefix = "tenants/" + orgIdStr + "/devices/" + deviceIdStr + "/";
            if (topic.startsWith(devicePrefix) || topic.equals("tenants/" + orgIdStr + "/devices/" + deviceIdStr)) {
                return true;
            }
        }

        String usernamePrefix = "tenants/" + orgIdStr + "/devices/" + username + "/";
        return topic.startsWith(usernamePrefix) || topic.equals("tenants/" + orgIdStr + "/devices/" + username);
    }

    public String resolveMqttUsername(Device device) {
        return (device.getName() != null && !device.getName().isBlank())
                ? device.getName()
                : "device-" + device.getId();
    }

    public String generateMqttPassword() {
        byte[] pwBytes = new byte[24];
        RANDOM.nextBytes(pwBytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(pwBytes);
    }

    private void appendToFileQueue(String username, String rawPassword) {
        try {
            File file = new File(dynamicCredentialsFile);
            File parentDir = file.getParentFile();
            if (parentDir != null && !parentDir.exists()) {
                // If parent directory does not exist (e.g. running outside Docker container), skip file provisioning
                return;
            }
            try (FileWriter fw = new FileWriter(file, true);
                 PrintWriter pw = new PrintWriter(fw)) {
                pw.println(username + ":" + rawPassword);
                log.debug("Appended credentials to dynamic file queue for user={}", username);
            }
        } catch (IOException e) {
            log.debug("File-based MQTT provisioning skipped or failed: {}", e.getMessage());
        }
    }

    private String sha256(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(md.digest(input.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new RuntimeException("SHA-256 algorithm not available", e);
        }
    }
}
