package com.sentinel.iot.converter;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sentinel.iot.model.SensorReading;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;

/**
 * JPA converter for the Telemetry.readings field.
 * Persists as PostgreSQL JSONB text; Hibernate reads/writes it as Map<String, SensorReading>.
 */
@Converter
@Slf4j
public class SensorReadingsConverter implements AttributeConverter<Map<String, SensorReading>, String> {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final TypeReference<Map<String, SensorReading>> TYPE =
            new TypeReference<>() {};

    @Override
    public String convertToDatabaseColumn(Map<String, SensorReading> readings) {
        if (readings == null || readings.isEmpty()) return null;
        try {
            return MAPPER.writeValueAsString(readings);
        } catch (Exception e) {
            log.error("Failed to serialize sensor readings to JSONB", e);
            return null;
        }
    }

    @Override
    public Map<String, SensorReading> convertToEntityAttribute(String json) {
        if (json == null || json.isBlank()) return null;
        try {
            return MAPPER.readValue(json, TYPE);
        } catch (Exception e) {
            log.error("Failed to deserialize sensor readings from JSONB: {}", json, e);
            return null;
        }
    }
}
