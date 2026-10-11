package com.sentinel.iot;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sentinel.iot.dto.TelemetryMessage;
import com.sentinel.iot.industrial.IndustrialProtocolBridgeService;
import com.sentinel.iot.industrial.dto.ModbusIngressRequest;
import com.sentinel.iot.industrial.dto.ModbusPoint;
import com.sentinel.iot.industrial.dto.OpcUaIngressRequest;
import com.sentinel.iot.industrial.dto.OpcUaNodeValue;
import com.sentinel.iot.kafka.KafkaTelemetryProducer;
import com.sentinel.iot.model.ReadingQuality;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

@Tag("unit")
@DisplayName("IndustrialProtocolBridgeService")
@ExtendWith(MockitoExtension.class)
class IndustrialProtocolBridgeTest {

    @Mock
    private KafkaTelemetryProducer kafkaProducer;

    @Spy
    private ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks
    private IndustrialProtocolBridgeService bridgeService;

    private UUID orgId;

    @BeforeEach
    void setUp() {
        orgId = UUID.randomUUID();
    }

    @Test
    @DisplayName("translateModbus: scales registers and produces schema v2 telemetry")
    void translateModbus_convertsScaledPointsCorrectly() {
        // Raw holding register 255 with scale 0.1 and offset -5.0 => 25.5 - 5.0 = 20.5
        ModbusPoint p1 = new ModbusPoint("PRESSURE", 255, "bar", 0.1, -5.0, ReadingQuality.GOOD);
        ModbusPoint p2 = new ModbusPoint("MOTOR_SPEED", 1500, "RPM", 1.0, 0.0, ReadingQuality.GOOD);

        ModbusIngressRequest req = new ModbusIngressRequest(
                "plc-line-1", orgId, 1, List.of(p1, p2), null, 1728660000000L
        );

        TelemetryMessage msg = bridgeService.translateModbus(req);

        assertThat(msg.getDeviceId()).isEqualTo("plc-line-1");
        assertThat(msg.getOrganizationId()).isEqualTo(orgId);
        assertThat(msg.getSchemaVersion()).isEqualTo(2);
        assertThat(msg.getEdge().getProtocol()).isEqualTo("MODBUS_TCP");

        assertThat(msg.getReadings()).containsKey("PRESSURE");
        assertThat(msg.getReadings().get("PRESSURE").value()).isEqualTo(20.5);
        assertThat(msg.getReadings().get("PRESSURE").unit()).isEqualTo("bar");
        assertThat(msg.getReadings().get("PRESSURE").quality()).isEqualTo(ReadingQuality.GOOD);

        assertThat(msg.getReadings()).containsKey("MOTOR_SPEED");
        assertThat(msg.getReadings().get("MOTOR_SPEED").value()).isEqualTo(1500.0);
    }

    @Test
    @DisplayName("translateModbus: marks readings as BAD when Modbus exception code is returned")
    void translateModbus_handlesExceptionCodeAsBadQuality() {
        ModbusPoint p1 = new ModbusPoint("FLOW_RATE", 0, "L/min", 1.0, 0.0, null);
        // Modbus exception 0x02 = ILLEGAL_DATA_ADDRESS
        ModbusIngressRequest req = new ModbusIngressRequest(
                "plc-flow-meter", orgId, 2, List.of(p1), 2, 1728660000000L
        );

        TelemetryMessage msg = bridgeService.translateModbus(req);

        assertThat(msg.getReadings().get("FLOW_RATE").quality()).isEqualTo(ReadingQuality.BAD);
        assertThat(msg.getReadings().get("FLOW_RATE").value()).isNull();
        assertThat(msg.getReadings().get("FLOW_RATE").isUsable()).isFalse();
    }

    @Test
    @DisplayName("translateOpcUa: maps Good, Uncertain, and Bad status codes to ReadingQuality")
    void translateOpcUa_mapsStatusCodesCorrectly() {
        OpcUaNodeValue goodNode = new OpcUaNodeValue("ns=2;s=Boiler.Temp", "BOILER_TEMP", 82.4, "°C", "Good", 1728660000000L);
        OpcUaNodeValue uncertainNode = new OpcUaNodeValue("ns=2;s=Pump.Vibration", "VIBRATION", 1.2, "mm/s", "Uncertain_InitialValue", 1728660000000L);
        OpcUaNodeValue badNode = new OpcUaNodeValue("ns=2;s=Tank.Level", "TANK_LEVEL", null, "%", "Bad_SensorFailure", 1728660000000L);

        OpcUaIngressRequest req = new OpcUaIngressRequest(
                "opcua-gateway-01", orgId, "opc.tcp://192.168.1.50:4840",
                List.of(goodNode, uncertainNode, badNode), 1728660000000L
        );

        TelemetryMessage msg = bridgeService.translateOpcUa(req);

        assertThat(msg.getDeviceId()).isEqualTo("opcua-gateway-01");
        assertThat(msg.getEdge().getProtocol()).isEqualTo("OPC_UA");

        assertThat(msg.getReadings().get("BOILER_TEMP").quality()).isEqualTo(ReadingQuality.GOOD);
        assertThat(msg.getReadings().get("BOILER_TEMP").value()).isEqualTo(82.4);

        assertThat(msg.getReadings().get("VIBRATION").quality()).isEqualTo(ReadingQuality.UNCERTAIN);
        assertThat(msg.getReadings().get("VIBRATION").value()).isEqualTo(1.2);

        assertThat(msg.getReadings().get("TANK_LEVEL").quality()).isEqualTo(ReadingQuality.BAD);
        assertThat(msg.getReadings().get("TANK_LEVEL").value()).isNull();
    }

    @Test
    @DisplayName("ingestModbus: translates and publishes to Kafka with multi-tenant partition key")
    void ingestModbus_publishesToKafkaWithMultiTenantKey() {
        ModbusPoint p1 = new ModbusPoint("CURRENT", 12.5, "A", 1.0, 0.0, ReadingQuality.GOOD);
        ModbusIngressRequest req = new ModbusIngressRequest(
                "vfd-drive-3", orgId, 1, List.of(p1), null, 1728660000000L
        );

        TelemetryMessage msg = bridgeService.ingestModbus(req);

        assertThat(msg.getDeviceId()).isEqualTo("vfd-drive-3");

        String expectedPartitionKey = orgId + ":vfd-drive-3";
        ArgumentCaptor<String> payloadCaptor = ArgumentCaptor.forClass(String.class);
        verify(kafkaProducer).publish(eq(expectedPartitionKey), payloadCaptor.capture());

        assertThat(payloadCaptor.getValue()).contains("\"CURRENT\":{").contains("\"value\":12.5").contains("\"unit\":\"A\"").contains("\"quality\":\"GOOD\"");
        assertThat(payloadCaptor.getValue()).contains("\"protocol\":\"MODBUS_TCP\"");
    }

    @Test
    @DisplayName("ingestOpcUa: translates and publishes to Kafka with multi-tenant partition key")
    void ingestOpcUa_publishesToKafkaWithMultiTenantKey() {
        OpcUaNodeValue node = new OpcUaNodeValue("ns=1;s=PowerMeter.kW", "POWER_KW", 45.2, "kW", "Good", 1728660000000L);
        OpcUaIngressRequest req = new OpcUaIngressRequest(
                "substation-meter", orgId, "opc.tcp://10.10.1.10:4840",
                List.of(node), 1728660000000L
        );

        TelemetryMessage msg = bridgeService.ingestOpcUa(req);

        assertThat(msg.getDeviceId()).isEqualTo("substation-meter");

        String expectedPartitionKey = orgId + ":substation-meter";
        ArgumentCaptor<String> payloadCaptor = ArgumentCaptor.forClass(String.class);
        verify(kafkaProducer).publish(eq(expectedPartitionKey), payloadCaptor.capture());

        assertThat(payloadCaptor.getValue()).contains("\"POWER_KW\":{").contains("\"value\":45.2").contains("\"unit\":\"kW\"").contains("\"quality\":\"GOOD\"");
        assertThat(payloadCaptor.getValue()).contains("\"protocol\":\"OPC_UA\"");
    }
}
