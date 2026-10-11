package com.sentinel.iot.service;

import com.sentinel.iot.model.Device;
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
import java.util.HexFormat;
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

        // 2. Redis Tier: sub-millisecond dynamic auth lookup
        try {
            redisTemplate.opsForValue().set("mqtt:auth:" + username, passwordHash);
            String acl = "factory/telemetry,tenants/" + device.getOrganizationId() + "/#";
            redisTemplate.opsForValue().set("mqtt:acl:" + username, acl);
            redisTemplate.opsForValue().set("mqtt:device:" + username, device.getId().toString());
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
     * Verifies if a given device username and password are valid against DB / Redis.
     */
    public boolean verifyCredentials(String username, String rawPassword) {
        if (username == null || rawPassword == null) return false;
        String passwordHash = sha256(rawPassword);

        // Try Redis first
        try {
            String cachedHash = redisTemplate.opsForValue().get("mqtt:auth:" + username);
            if (cachedHash != null) {
                return cachedHash.equals(passwordHash);
            }
        } catch (Exception ignored) {
        }

        // Fallback to database
        Optional<Device> devOpt = deviceRepository.findByMqttUsername(username);
        return devOpt.map(device -> passwordHash.equals(device.getMqttPasswordHash())).orElse(false);
    }

    public String resolveMqttUsername(Device device) {
        return (device.getName() != null && !device.getName().isBlank())
                ? device.getName()
                : "device-" + device.getId();
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
