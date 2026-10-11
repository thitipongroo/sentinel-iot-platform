package com.sentinel.iot.industrial;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sentinel.iot.dto.TelemetryMessage;
import com.sentinel.iot.industrial.dto.ModbusIngressRequest;
import com.sentinel.iot.industrial.dto.ModbusPoint;
import com.sentinel.iot.industrial.dto.OpcUaIngressRequest;
import com.sentinel.iot.industrial.dto.OpcUaNodeValue;
import com.sentinel.iot.kafka.KafkaTelemetryProducer;
import com.sentinel.iot.model.EdgeMetadata;
import com.sentinel.iot.model.ReadingQuality;
import com.sentinel.iot.model.SensorReading;
import com.sentinel.iot.security.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * High-throughput protocol translation engine converting raw industrial fieldbus
 * payloads (Modbus TCP registers, OPC-UA MonitoredItems) into standardized
 * Sentinel v2 Telemetry JSON and forwarding to the Kafka ingestion pipeline.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class IndustrialProtocolBridgeService {

    private final KafkaTelemetryProducer kafkaProducer;
    private final ObjectMapper objectMapper;

    /**
     * Translates a Modbus TCP register poll into a standard Sentinel v2 TelemetryMessage.
     */
    public TelemetryMessage translateModbus(ModbusIngressRequest req) {
        TelemetryMessage msg = new TelemetryMessage();
        msg.setDeviceId(req.deviceId());

        UUID orgId = (req.organizationId() != null) ? req.organizationId() : TenantContext.get();
        msg.setOrganizationId(orgId);
        msg.setSchemaVersion(2);
        msg.setTimestamp(req.timestamp() != null ? req.timestamp() : Instant.now().toEpochMilli());

        EdgeMetadata edge = new EdgeMetadata();
        edge.setProtocol("MODBUS_TCP");
        edge.setFirmwareVersion("modbus-bridge-v1.0");
        msg.setEdge(edge);

        Map<String, SensorReading> readings = new LinkedHashMap<>();
        boolean isException = (req.exceptionCode() != null && req.exceptionCode() != 0);

        if (req.points() != null) {
            for (ModbusPoint point : req.points()) {
                if (isException) {
                    readings.put(point.sensorKey(), SensorReading.bad(point.unit()));
                } else {
                    double val = point.getScaledValue();
                    ReadingQuality q = (point.quality() != null) ? point.quality() : ReadingQuality.GOOD;
                    readings.put(point.sensorKey(), new SensorReading(val, point.unit(), q));
                }
            }
        }
        msg.setReadings(readings);
        return msg;
    }

    /**
     * Translates OPC-UA DataChange notifications into a standard Sentinel v2 TelemetryMessage.
     */
    public TelemetryMessage translateOpcUa(OpcUaIngressRequest req) {
        TelemetryMessage msg = new TelemetryMessage();
        msg.setDeviceId(req.deviceId());

        UUID orgId = (req.organizationId() != null) ? req.organizationId() : TenantContext.get();
        msg.setOrganizationId(orgId);
        msg.setSchemaVersion(2);
        msg.setTimestamp(req.timestamp() != null ? req.timestamp() : Instant.now().toEpochMilli());

        EdgeMetadata edge = new EdgeMetadata();
        edge.setProtocol("OPC_UA");
        edge.setFirmwareVersion("opcua-bridge-v1.0");
        msg.setEdge(edge);

        Map<String, SensorReading> readings = new LinkedHashMap<>();
        if (req.nodes() != null) {
            for (OpcUaNodeValue node : req.nodes()) {
                String sensorKey = (node.sensorKey() != null && !node.sensorKey().isBlank())
                        ? node.sensorKey()
                        : node.nodeId();
                ReadingQuality quality = node.resolveQuality();
                Double val = (quality == ReadingQuality.BAD) ? null : node.value();
                readings.put(sensorKey, new SensorReading(val, node.unit(), quality));
            }
        }
        msg.setReadings(readings);
        return msg;
    }

    /**
     * Forwards translated TelemetryMessage to Kafka with tenant anti-collision key.
     */
    public TelemetryMessage forwardToKafka(TelemetryMessage msg) {
        String partitionKey = (msg.getOrganizationId() != null ? msg.getOrganizationId() + ":" : "") + msg.getDeviceId();
        try {
            String payloadJson = objectMapper.writeValueAsString(msg);
            kafkaProducer.publish(partitionKey, payloadJson);
            log.debug("Forwarded industrial telemetry for device={} to Kafka (key={})", msg.getDeviceId(), partitionKey);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize TelemetryMessage for device {}: {}", msg.getDeviceId(), e.getMessage());
            throw new IllegalArgumentException("Failed to serialize industrial telemetry: " + e.getMessage(), e);
        }
        return msg;
    }

    /**
     * Translates and forwards Modbus TCP payload in a single step.
     */
    public TelemetryMessage ingestModbus(ModbusIngressRequest req) {
        TelemetryMessage msg = translateModbus(req);
        return forwardToKafka(msg);
    }

    /**
     * Translates and forwards OPC-UA payload in a single step.
     */
    public TelemetryMessage ingestOpcUa(OpcUaIngressRequest req) {
        TelemetryMessage msg = translateOpcUa(req);
        return forwardToKafka(msg);
    }
}
