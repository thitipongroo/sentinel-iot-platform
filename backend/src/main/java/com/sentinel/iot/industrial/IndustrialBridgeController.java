package com.sentinel.iot.industrial;

import com.sentinel.iot.dto.TelemetryMessage;
import com.sentinel.iot.industrial.dto.ModbusIngressRequest;
import com.sentinel.iot.industrial.dto.OpcUaIngressRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * REST controller for industrial protocol translation and edge ingestion gateway.
 */
@RestController
@RequestMapping("/api/v1/industrial")
@RequiredArgsConstructor
@Tag(name = "Industrial Protocols & Edge Gateway", description = "Translates Modbus TCP and OPC-UA payloads into Sentinel v2 telemetry")
public class IndustrialBridgeController {

    private final IndustrialProtocolBridgeService bridgeService;

    @PostMapping("/modbus/ingest")
    @Operation(summary = "Translate and ingest Modbus TCP payload into platform",
               description = "Converts Modbus registers to Sentinel v2 telemetry JSON and publishes directly to Kafka.")
    public ResponseEntity<TelemetryMessage> ingestModbus(@Valid @RequestBody ModbusIngressRequest request) {
        TelemetryMessage msg = bridgeService.ingestModbus(request);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(msg);
    }

    @PostMapping("/opcua/ingest")
    @Operation(summary = "Translate and ingest OPC-UA DataChange payload into platform",
               description = "Converts OPC-UA MonitoredItem values to Sentinel v2 telemetry JSON and publishes to Kafka.")
    public ResponseEntity<TelemetryMessage> ingestOpcUa(@Valid @RequestBody OpcUaIngressRequest request) {
        TelemetryMessage msg = bridgeService.ingestOpcUa(request);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(msg);
    }

    @PostMapping("/modbus/translate")
    @Operation(summary = "Translate Modbus TCP payload without forwarding (dry-run)",
               description = "Inspects the generated Sentinel v2 TelemetryMessage without publishing to Kafka.")
    public ResponseEntity<TelemetryMessage> translateModbus(@Valid @RequestBody ModbusIngressRequest request) {
        return ResponseEntity.ok(bridgeService.translateModbus(request));
    }

    @PostMapping("/opcua/translate")
    @Operation(summary = "Translate OPC-UA payload without forwarding (dry-run)",
               description = "Inspects the generated Sentinel v2 TelemetryMessage without publishing to Kafka.")
    public ResponseEntity<TelemetryMessage> translateOpcUa(@Valid @RequestBody OpcUaIngressRequest request) {
        return ResponseEntity.ok(bridgeService.translateOpcUa(request));
    }
}
