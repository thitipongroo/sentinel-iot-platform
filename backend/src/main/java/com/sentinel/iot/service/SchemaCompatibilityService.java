package com.sentinel.iot.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.avro.Schema;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Registers Avro schemas from {@code classpath:avro/*.avsc} with Confluent Schema Registry
 * on startup, and validates BACKWARD compatibility before registering a new version.
 *
 * <p>Startup is aborted if any schema is incompatible with the version already in the registry.
 * This makes schema-breaking changes a deployment failure rather than a runtime surprise.
 *
 * <p>Set {@code schema-registry.enabled=false} in environments without a registry (local dev,
 * unit test). When disabled, this runner is a no-op.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SchemaCompatibilityService implements ApplicationRunner {

    private final ResourceLoader resourceLoader;
    private final ObjectMapper   objectMapper;

    @Value("${schema-registry.url:http://localhost:8081}")
    private String registryUrl;

    @Value("${schema-registry.enabled:false}")
    private boolean enabled;

    // Schema load order matters: referenced types must be parsed before referencing types.
    private static final LinkedHashMap<String, String> SUBJECTS = new LinkedHashMap<>(Map.of(
            "sentinel.SensorReading-value",    "classpath:avro/SensorReading.avsc",
            "sentinel.EdgeMetadata-value",     "classpath:avro/EdgeMetadata.avsc",
            "sentinel.TelemetryMessage-value", "classpath:avro/TelemetryMessage.avsc"
    ));

    @Override
    public void run(ApplicationArguments args) {
        if (!enabled) {
            log.info("Schema Registry integration disabled — skipping schema registration");
            return;
        }

        RestTemplate rest = new RestTemplate();
        Schema.Parser parser = new Schema.Parser();

        for (Map.Entry<String, String> entry : SUBJECTS.entrySet()) {
            String subject     = entry.getKey();
            String classpathLoc = entry.getValue();

            try {
                String schemaJson = loadResource(classpathLoc);
                Schema schema     = parser.parse(schemaJson); // validates JSON + Avro syntax

                if (hasExistingVersion(rest, subject)) {
                    assertCompatible(rest, subject, schemaJson);
                }

                int id = registerSchema(rest, subject, schemaJson);
                log.info("Schema registered: subject={} avroName={} registryId={}", subject, schema.getFullName(), id);

            } catch (SchemaIncompatibleException e) {
                throw new IllegalStateException(
                    "Schema compatibility check FAILED for subject '" + subject + "'. " +
                    "Aborting startup to prevent data corruption. " +
                    "Introduce a new API version or fix the schema change.", e);
            } catch (Exception e) {
                // Registry unreachable: warn but don't block startup
                log.warn("Could not register schema for subject={}: {} — continuing without registry enforcement",
                        subject, e.getMessage());
            }
        }
    }

    // ── Schema Registry REST helpers ──────────────────────────────────────────

    private boolean hasExistingVersion(RestTemplate rest, String subject) {
        try {
            rest.getForEntity(registryUrl + "/subjects/" + subject + "/versions/latest", String.class);
            return true;
        } catch (HttpClientErrorException.NotFound e) {
            return false;
        }
    }

    private void assertCompatible(RestTemplate rest, String subject, String schemaJson) {
        String url  = registryUrl + "/compatibility/subjects/" + subject + "/versions/latest";
        String body = buildBody(schemaJson);

        try {
            ResponseEntity<JsonNode> response = rest.postForEntity(
                    url, jsonEntity(body), JsonNode.class);
            JsonNode json = response.getBody();
            if (json == null || !json.path("is_compatible").asBoolean(true)) {
                throw new SchemaIncompatibleException(subject,
                        json != null ? json.toString() : "null response from registry");
            }
            log.debug("Schema compatibility check PASSED for subject={}", subject);
        } catch (HttpClientErrorException e) {
            throw new SchemaIncompatibleException(subject, e.getResponseBodyAsString());
        }
    }

    private int registerSchema(RestTemplate rest, String subject, String schemaJson) {
        String url  = registryUrl + "/subjects/" + subject + "/versions";
        String body = buildBody(schemaJson);

        JsonNode response = rest.postForObject(url, jsonEntity(body), JsonNode.class);
        if (response == null || !response.has("id")) {
            throw new IllegalStateException("Unexpected response from Schema Registry for subject: " + subject);
        }
        return response.get("id").asInt();
    }

    private String buildBody(String schemaJson) {
        ObjectNode node = objectMapper.createObjectNode();
        node.put("schemaType", "AVRO");
        node.put("schema", schemaJson);
        return node.toString();
    }

    private HttpEntity<String> jsonEntity(String body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType("application/vnd.schemaregistry.v1+json"));
        return new HttpEntity<>(body, headers);
    }

    @SuppressWarnings("null")
    private String loadResource(String location) throws IOException {
        Resource resource = resourceLoader.getResource(location);
        return resource.getContentAsString(StandardCharsets.UTF_8);
    }

    // ── Domain exception ──────────────────────────────────────────────────────

    public static class SchemaIncompatibleException extends RuntimeException {
        public SchemaIncompatibleException(String subject, String detail) {
            super("Schema for subject '" + subject + "' is not BACKWARD compatible. Detail: " + detail);
        }
    }
}
