package com.sentinel.iot;

import com.sentinel.iot.converter.DeviceCapabilitiesConverter;
import com.sentinel.iot.model.SensorCapability;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("unit")
@DisplayName("DeviceCapabilitiesConverter")
class DeviceCapabilitiesConverterTest {

    private final DeviceCapabilitiesConverter converter = new DeviceCapabilitiesConverter();

    // ── convertToDatabaseColumn ───────────────────────────────────────────────

    @Nested
    @DisplayName("convertToDatabaseColumn")
    class ConvertToDatabaseColumn {

        @Test
        @DisplayName("null input returns null")
        void null_returnsNull() {
            assertThat(converter.convertToDatabaseColumn(null)).isNull();
        }

        @Test
        @DisplayName("empty map returns null")
        void emptyMap_returnsNull() {
            assertThat(converter.convertToDatabaseColumn(Map.of())).isNull();
        }

        @Test
        @DisplayName("valid map serializes to a JSON string containing the sensor key and threshold")
        void validMap_returnsJsonContainingSensorData() {
            Map<String, SensorCapability> caps = Map.of(
                    "TEMPERATURE", SensorCapability.above("°C", 70.0, 85.0, 1));

            String json = converter.convertToDatabaseColumn(caps);

            assertThat(json)
                    .as("serialized JSONB must be non-null and contain sensor key")
                    .isNotNull()
                    .contains("TEMPERATURE")
                    .contains("70.0");
        }
    }

    // ── convertToEntityAttribute ──────────────────────────────────────────────

    @Nested
    @DisplayName("convertToEntityAttribute")
    class ConvertToEntityAttribute {

        @Test
        @DisplayName("null input returns null")
        void null_returnsNull() {
            assertThat(converter.convertToEntityAttribute(null)).isNull();
        }

        @Test
        @DisplayName("blank string returns null")
        void blank_returnsNull() {
            assertThat(converter.convertToEntityAttribute("   ")).isNull();
        }

        @Test
        @DisplayName("valid JSON round-trips to a map preserving the warn threshold")
        void validJson_roundTripsCapability() {
            SensorCapability original = SensorCapability.above("°C", 70.0, 85.0, 1);
            String json = converter.convertToDatabaseColumn(Map.of("TEMPERATURE", original));

            Map<String, SensorCapability> result = converter.convertToEntityAttribute(json);

            assertThat(result)
                    .as("deserialized map must contain the TEMPERATURE key")
                    .isNotNull()
                    .containsKey("TEMPERATURE");
            assertThat(result.get("TEMPERATURE").warnThreshold())
                    .as("warn threshold must survive the JSON round-trip")
                    .isEqualTo(70.0);
        }

        @Test
        @DisplayName("invalid JSON returns null instead of propagating the parse exception")
        void invalidJson_returnsNull() {
            assertThat(converter.convertToEntityAttribute("not-valid-json")).isNull();
        }
    }
}
