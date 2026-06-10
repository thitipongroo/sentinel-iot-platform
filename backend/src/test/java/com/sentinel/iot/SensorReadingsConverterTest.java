package com.sentinel.iot;

import com.sentinel.iot.converter.SensorReadingsConverter;
import com.sentinel.iot.model.ReadingQuality;
import com.sentinel.iot.model.SensorReading;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("unit")
@DisplayName("SensorReadingsConverter")
class SensorReadingsConverterTest {

    private final SensorReadingsConverter converter = new SensorReadingsConverter();

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
        @DisplayName("valid map serializes to a JSON string containing the sensor key and value")
        void validMap_returnsJsonContainingSensorData() {
            Map<String, SensorReading> readings = Map.of(
                    "TEMPERATURE", SensorReading.good(22.5, "°C"));

            String json = converter.convertToDatabaseColumn(readings);

            assertThat(json)
                    .as("serialized JSONB must be non-null and contain sensor key")
                    .isNotNull()
                    .contains("TEMPERATURE")
                    .contains("22.5");
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
        @DisplayName("valid JSON round-trips to a map preserving the value and quality")
        void validJson_roundTripsReading() {
            SensorReading original = SensorReading.good(22.5, "°C");
            String json = converter.convertToDatabaseColumn(Map.of("TEMPERATURE", original));

            Map<String, SensorReading> result = converter.convertToEntityAttribute(json);

            assertThat(result)
                    .as("deserialized map must contain the TEMPERATURE key")
                    .isNotNull()
                    .containsKey("TEMPERATURE");
            assertThat(result.get("TEMPERATURE").value())
                    .as("sensor value must survive the JSON round-trip")
                    .isEqualTo(22.5);
            assertThat(result.get("TEMPERATURE").quality())
                    .as("reading quality must survive the JSON round-trip")
                    .isEqualTo(ReadingQuality.GOOD);
        }

        @Test
        @DisplayName("invalid JSON returns null instead of propagating the parse exception")
        void invalidJson_returnsNull() {
            assertThat(converter.convertToEntityAttribute("not-valid-json")).isNull();
        }
    }
}
