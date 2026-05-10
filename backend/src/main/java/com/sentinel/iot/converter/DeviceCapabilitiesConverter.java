package com.sentinel.iot.converter;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sentinel.iot.model.SensorCapability;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;

/**
 * JPA converter for Device.capabilities.
 * Map key is SensorType.name() (e.g. "TEMPERATURE"); value is the SensorCapability config.
 */
@Converter
@Slf4j
public class DeviceCapabilitiesConverter implements AttributeConverter<Map<String, SensorCapability>, String> {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final TypeReference<Map<String, SensorCapability>> TYPE =
            new TypeReference<>() {};

    @Override
    public String convertToDatabaseColumn(Map<String, SensorCapability> capabilities) {
        if (capabilities == null || capabilities.isEmpty()) return null;
        try {
            return MAPPER.writeValueAsString(capabilities);
        } catch (Exception e) {
            log.error("Failed to serialize device capabilities to JSONB", e);
            return null;
        }
    }

    @Override
    public Map<String, SensorCapability> convertToEntityAttribute(String json) {
        if (json == null || json.isBlank()) return null;
        try {
            return MAPPER.readValue(json, TYPE);
        } catch (Exception e) {
            log.error("Failed to deserialize device capabilities from JSONB: {}", json, e);
            return null;
        }
    }
}
