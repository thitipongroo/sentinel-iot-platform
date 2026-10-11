package com.sentinel.iot.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sentinel.iot.dto.DeviceShadowDeltaNotification;
import com.sentinel.iot.dto.DeviceShadowDocument;
import com.sentinel.iot.dto.DeviceShadowState;
import com.sentinel.iot.dto.DeviceShadowUpdateRequest;
import com.sentinel.iot.exception.DeviceShadowConflictException;
import com.sentinel.iot.model.Device;
import com.sentinel.iot.model.DeviceShadow;
import com.sentinel.iot.repository.DeviceRepository;
import com.sentinel.iot.repository.DeviceShadowRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.*;

/**
 * Service orchestrating Device Twin / Shadow state synchronization,
 * virtual delta calculation, OCC versioning, Redis sub-millisecond caching,
 * and MQTT delta notifications.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class DeviceShadowService {

    private static final Duration SHADOW_CACHE_TTL = Duration.ofHours(24);
    private static final String REDIS_SHADOW_KEY_PREFIX = "device:shadow:";

    private final DeviceShadowRepository shadowRepository;
    private final DeviceRepository deviceRepository;
    @Qualifier("redisTemplate")
    private final StringRedisTemplate redisTemplate;
    private final MqttPublisherService mqttPublisher;
    private final ObjectMapper objectMapper;

    /**
     * Retrieve the shadow document for a device. Checks Redis first, falling back to PostgreSQL.
     */
    @Transactional(readOnly = true)
    public DeviceShadowDocument getShadow(UUID deviceId, UUID orgId) {
        String cacheKey = buildCacheKey(orgId, deviceId);
        try {
            String cached = redisTemplate.opsForValue().get(cacheKey);
            if (cached != null && !cached.isBlank()) {
                return objectMapper.readValue(cached, DeviceShadowDocument.class);
            }
        } catch (Exception e) {
            log.warn("Failed to read device shadow from Redis cache: {}", e.getMessage());
        }

        // Verify device existence under tenant
        deviceRepository.findByIdAndOrganizationId(deviceId, orgId)
                .orElseThrow(() -> new NoSuchElementException("Device not found: " + deviceId));

        DeviceShadow shadow = shadowRepository.findByDeviceIdAndOrganizationId(deviceId, orgId)
                .orElseGet(() -> createInitialShadow(deviceId, orgId));

        DeviceShadowDocument doc = toDocument(shadow);
        cacheShadow(cacheKey, doc);
        return doc;
    }

    /**
     * Apply updates to desired and/or reported states with OCC version checking.
     */
    @Transactional
    public DeviceShadowDocument updateShadow(UUID deviceId, UUID orgId, DeviceShadowUpdateRequest request) {
        if (request.desired() == null && request.reported() == null) {
            return getShadow(deviceId, orgId);
        }

        DeviceShadow shadow = getOrCreateEntity(deviceId, orgId);

        // Optimistic Concurrency Control (OCC)
        if (request.version() != null && !request.version().equals(shadow.getVersion())) {
            throw new DeviceShadowConflictException(
                    String.format("Version conflict. Expected version %d, but current shadow version is %d.",
                            request.version(), shadow.getVersion()),
                    shadow.getVersion());
        }

        Instant now = Instant.now();
        long nowEpochSec = now.getEpochSecond();
        Map<String, Object> metadata = ensureMetadataStructure(shadow.getMetadata());

        boolean desiredModified = false;
        boolean reportedModified = false;

        // Apply desired state patch
        if (request.desired() != null) {
            Map<String, Object> desired = shadow.getDesiredState();
            @SuppressWarnings("unchecked")
            Map<String, Object> desiredMeta = (Map<String, Object>) metadata.get("desired");

            for (Map.Entry<String, Object> entry : request.desired().entrySet()) {
                String key = entry.getKey();
                Object val = entry.getValue();
                if (val == null) {
                    desired.remove(key);
                } else {
                    desired.put(key, val);
                }
                desiredMeta.put(key, Map.of("timestamp", nowEpochSec));
            }
            desiredModified = true;
        }

        // Apply reported state patch
        if (request.reported() != null) {
            Map<String, Object> reported = shadow.getReportedState();
            @SuppressWarnings("unchecked")
            Map<String, Object> reportedMeta = (Map<String, Object>) metadata.get("reported");

            for (Map.Entry<String, Object> entry : request.reported().entrySet()) {
                String key = entry.getKey();
                Object val = entry.getValue();
                if (val == null) {
                    reported.remove(key);
                } else {
                    reported.put(key, val);
                }
                reportedMeta.put(key, Map.of("timestamp", nowEpochSec));
            }
            reportedModified = true;
        }

        shadow.setMetadata(metadata);
        shadow.setVersion(shadow.getVersion() + 1);
        shadow.setUpdatedAt(now);

        DeviceShadow saved = shadowRepository.save(shadow);
        DeviceShadowDocument doc = toDocument(saved);

        // Update Redis cache
        String cacheKey = buildCacheKey(orgId, deviceId);
        cacheShadow(cacheKey, doc);

        // Compute delta and notify if needed
        Map<String, Object> delta = doc.state().delta();
        if (desiredModified && delta != null && !delta.isEmpty()) {
            publishDeltaNotification(deviceId, orgId, delta, metadata, saved.getVersion(), nowEpochSec, request.clientToken());
        }

        return doc;
    }

    /**
     * Delete / reset shadow for a device and clear cache.
     */
    @Transactional
    public void deleteShadow(UUID deviceId, UUID orgId) {
        shadowRepository.deleteByDeviceIdAndOrganizationId(deviceId, orgId);
        try {
            redisTemplate.delete(buildCacheKey(orgId, deviceId));
        } catch (Exception e) {
            log.warn("Failed to delete device shadow from Redis: {}", e.getMessage());
        }
    }

    /**
     * Compute virtual delta: delta = { k in desired | reported[k] != desired[k] }
     */
    public Map<String, Object> computeDelta(Map<String, Object> desired, Map<String, Object> reported) {
        if (desired == null || desired.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<String, Object> delta = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : desired.entrySet()) {
            String key = entry.getKey();
            Object desiredVal = entry.getValue();
            if (reported == null || !reported.containsKey(key)) {
                delta.put(key, desiredVal);
            } else {
                Object reportedVal = reported.get(key);
                if (!Objects.equals(desiredVal, reportedVal)) {
                    if (desiredVal instanceof Map<?, ?> desiredSub && reportedVal instanceof Map<?, ?> reportedSub) {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> subDelta = computeDelta(
                                (Map<String, Object>) desiredSub,
                                (Map<String, Object>) reportedSub
                        );
                        if (!subDelta.isEmpty()) {
                            delta.put(key, subDelta);
                        }
                    } else {
                        delta.put(key, desiredVal);
                    }
                }
            }
        }
        return delta;
    }

    /**
     * Ingress handler for shadow MQTT messages from edge devices:
     * - tenants/{orgId}/devices/{deviceId}/shadow/update
     * - tenants/{orgId}/devices/{deviceId}/shadow/get
     */
    @Transactional
    public void handleMqttShadowMessage(String topic, String rawPayload) {
        String cleanTopic = stripSharePrefix(topic);
        String[] parts = cleanTopic.split("/");
        if (parts.length < 5 || !"tenants".equalsIgnoreCase(parts[0]) || !"devices".equalsIgnoreCase(parts[2]) || !"shadow".equalsIgnoreCase(parts[4])) {
            log.warn("Ignored unknown MQTT shadow topic: {}", topic);
            return;
        }

        UUID orgId;
        try {
            orgId = UUID.fromString(parts[1]);
        } catch (IllegalArgumentException e) {
            log.warn("Invalid organizationId in topic: {}", parts[1]);
            return;
        }

        String deviceIdOrName = parts[3];
        UUID deviceId = resolveDeviceId(orgId, deviceIdOrName);
        if (deviceId == null) {
            log.warn("Device not found for shadow topic: {}", topic);
            publishMqttRejected(orgId, deviceIdOrName, 404, "Device not found: " + deviceIdOrName, null, null);
            return;
        }

        String action = parts.length >= 6 ? parts[5].toLowerCase() : "get";

        if ("get".equals(action)) {
            handleMqttGet(orgId, deviceId);
        } else if ("update".equals(action)) {
            handleMqttUpdate(orgId, deviceId, rawPayload);
        }
    }

    private void handleMqttGet(UUID orgId, UUID deviceId) {
        try {
            DeviceShadowDocument doc = getShadow(deviceId, orgId);
            String acceptedTopic = String.format("tenants/%s/devices/%s/shadow/get/accepted", orgId, deviceId);
            mqttPublisher.publish(acceptedTopic, objectMapper.writeValueAsString(doc));
        } catch (Exception e) {
            log.error("Failed to handle shadow get: {}", e.getMessage());
            String rejectedTopic = String.format("tenants/%s/devices/%s/shadow/get/rejected", orgId, deviceId);
            publishMqttError(rejectedTopic, 500, e.getMessage(), null, null);
        }
    }

    private void handleMqttUpdate(UUID orgId, UUID deviceId, String rawPayload) {
        String clientToken = null;
        Long expectedVersion = null;
        try {
            JsonNode root = objectMapper.readTree(rawPayload);
            if (root.has("clientToken")) {
                clientToken = root.get("clientToken").asText();
            } else if (root.has("client_token")) {
                clientToken = root.get("client_token").asText();
            }

            if (root.has("version")) {
                expectedVersion = root.get("version").asLong();
            }

            Map<String, Object> reported = null;
            Map<String, Object> desired = null;

            if (root.has("state")) {
                JsonNode stateNode = root.get("state");
                if (stateNode.has("reported")) {
                    reported = objectMapper.convertValue(stateNode.get("reported"), Map.class);
                }
                if (stateNode.has("desired")) {
                    desired = objectMapper.convertValue(stateNode.get("desired"), Map.class);
                }
            } else {
                if (root.has("reported")) {
                    reported = objectMapper.convertValue(root.get("reported"), Map.class);
                }
                if (root.has("desired")) {
                    desired = objectMapper.convertValue(root.get("desired"), Map.class);
                }
            }

            DeviceShadowUpdateRequest req = new DeviceShadowUpdateRequest(desired, reported, expectedVersion, clientToken);
            DeviceShadowDocument doc = updateShadow(deviceId, orgId, req);

            // Publish update accepted
            String acceptedTopic = String.format("tenants/%s/devices/%s/shadow/update/accepted", orgId, deviceId);
            Map<String, Object> acceptedPayload = new LinkedHashMap<>();
            acceptedPayload.put("state", Map.of("reported", doc.state().reported()));
            acceptedPayload.put("metadata", doc.metadata());
            acceptedPayload.put("version", doc.version());
            acceptedPayload.put("timestamp", doc.timestamp());
            if (clientToken != null) {
                acceptedPayload.put("clientToken", clientToken);
            }
            mqttPublisher.publish(acceptedTopic, objectMapper.writeValueAsString(acceptedPayload));

        } catch (DeviceShadowConflictException conflictEx) {
            log.warn("Shadow update OCC conflict for device {}: {}", deviceId, conflictEx.getMessage());
            publishMqttRejected(orgId, deviceId.toString(), 409, conflictEx.getMessage(), conflictEx.getCurrentVersion(), clientToken);
        } catch (Exception e) {
            log.error("Failed to process MQTT shadow update for device {}: {}", deviceId, e.getMessage());
            publishMqttRejected(orgId, deviceId.toString(), 400, "Invalid shadow update: " + e.getMessage(), null, clientToken);
        }
    }

    private void publishMqttRejected(UUID orgId, String deviceIdentifier, int code, String message, Long currentVersion, String clientToken) {
        String rejectedTopic = String.format("tenants/%s/devices/%s/shadow/update/rejected", orgId, deviceIdentifier);
        publishMqttError(rejectedTopic, code, message, currentVersion, clientToken);
    }

    private void publishMqttError(String topic, int code, String message, Long currentVersion, String clientToken) {
        try {
            Map<String, Object> err = new LinkedHashMap<>();
            err.put("code", code);
            err.put("message", message);
            if (currentVersion != null) {
                err.put("currentVersion", currentVersion);
            }
            err.put("timestamp", Instant.now().getEpochSecond());
            if (clientToken != null) {
                err.put("clientToken", clientToken);
            }
            mqttPublisher.publish(topic, objectMapper.writeValueAsString(err));
        } catch (Exception e) {
            log.error("Failed to publish error to {}: {}", topic, e.getMessage());
        }
    }

    private void publishDeltaNotification(UUID deviceId, UUID orgId, Map<String, Object> delta,
                                          Map<String, Object> metadata, Long version, long timestamp, String clientToken) {
        try {
            String deltaTopic = String.format("tenants/%s/devices/%s/shadow/update/delta", orgId, deviceId);
            DeviceShadowDeltaNotification notification = new DeviceShadowDeltaNotification(
                    deviceId, orgId, delta, metadata, version, timestamp, clientToken
            );
            // Retain delta on broker so reconnecting offline devices receive it immediately
            mqttPublisher.publish(deltaTopic, objectMapper.writeValueAsString(notification), 1, true);
            log.info("Published retained delta notification for device {} on {}", deviceId, deltaTopic);
        } catch (Exception e) {
            log.error("Failed to serialize or publish delta notification: {}", e.getMessage());
        }
    }

    private DeviceShadow getOrCreateEntity(UUID deviceId, UUID orgId) {
        deviceRepository.findByIdAndOrganizationId(deviceId, orgId)
                .orElseThrow(() -> new NoSuchElementException("Device not found: " + deviceId));

        return shadowRepository.findByDeviceIdAndOrganizationId(deviceId, orgId)
                .orElseGet(() -> createInitialShadow(deviceId, orgId));
    }

    private DeviceShadow createInitialShadow(UUID deviceId, UUID orgId) {
        Map<String, Object> meta = new HashMap<>();
        meta.put("desired", new HashMap<String, Object>());
        meta.put("reported", new HashMap<String, Object>());

        DeviceShadow shadow = DeviceShadow.builder()
                .deviceId(deviceId)
                .organizationId(orgId)
                .version(1L)
                .desiredState(new HashMap<>())
                .reportedState(new HashMap<>())
                .metadata(meta)
                .updatedAt(Instant.now())
                .build();
        return shadowRepository.save(shadow);
    }

    private DeviceShadowDocument toDocument(DeviceShadow shadow) {
        Map<String, Object> desired = shadow.getDesiredState() != null ? shadow.getDesiredState() : Collections.emptyMap();
        Map<String, Object> reported = shadow.getReportedState() != null ? shadow.getReportedState() : Collections.emptyMap();
        Map<String, Object> delta = computeDelta(desired, reported);

        DeviceShadowState state = new DeviceShadowState(desired, reported, delta);
        long epochSec = shadow.getUpdatedAt() != null ? shadow.getUpdatedAt().getEpochSecond() : Instant.now().getEpochSecond();

        return new DeviceShadowDocument(
                shadow.getDeviceId(),
                shadow.getOrganizationId(),
                state,
                shadow.getMetadata() != null ? shadow.getMetadata() : Collections.emptyMap(),
                shadow.getVersion(),
                epochSec
        );
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> ensureMetadataStructure(Map<String, Object> meta) {
        Map<String, Object> root = (meta != null) ? new HashMap<>(meta) : new HashMap<>();
        if (!(root.get("desired") instanceof Map)) {
            root.put("desired", new HashMap<String, Object>());
        } else {
            root.put("desired", new HashMap<>((Map<String, Object>) root.get("desired")));
        }
        if (!(root.get("reported") instanceof Map)) {
            root.put("reported", new HashMap<String, Object>());
        } else {
            root.put("reported", new HashMap<>((Map<String, Object>) root.get("reported")));
        }
        return root;
    }

    private void cacheShadow(String key, DeviceShadowDocument doc) {
        try {
            redisTemplate.opsForValue().set(key, objectMapper.writeValueAsString(doc), SHADOW_CACHE_TTL);
        } catch (JsonProcessingException e) {
            log.warn("Failed to serialize shadow document for Redis cache: {}", e.getMessage());
        } catch (Exception e) {
            log.warn("Failed to write shadow to Redis: {}", e.getMessage());
        }
    }

    private String buildCacheKey(UUID orgId, UUID deviceId) {
        return REDIS_SHADOW_KEY_PREFIX + orgId + ":" + deviceId;
    }

    private UUID resolveDeviceId(UUID orgId, String deviceIdOrName) {
        try {
            return UUID.fromString(deviceIdOrName);
        } catch (IllegalArgumentException e) {
            return deviceRepository.findByNameAndOrganizationId(deviceIdOrName, orgId)
                    .map(Device::getId)
                    .orElse(null);
        }
    }

    private String stripSharePrefix(String topic) {
        if (topic != null && topic.startsWith("$share/")) {
            int secondSlash = topic.indexOf('/', "$share/".length());
            if (secondSlash != -1 && secondSlash + 1 < topic.length()) {
                return topic.substring(secondSlash + 1);
            }
        }
        return topic;
    }
}
