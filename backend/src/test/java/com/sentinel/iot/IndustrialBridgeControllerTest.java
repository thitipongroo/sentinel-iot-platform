package com.sentinel.iot;

import com.sentinel.iot.dto.TelemetryMessage;
import com.sentinel.iot.industrial.IndustrialBridgeController;
import com.sentinel.iot.industrial.IndustrialProtocolBridgeService;
import com.sentinel.iot.industrial.dto.ModbusIngressRequest;
import com.sentinel.iot.industrial.dto.OpcUaIngressRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

import java.util.Collections;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@Tag("unit")
@DisplayName("IndustrialBridgeController")
@ExtendWith(MockitoExtension.class)
class IndustrialBridgeControllerTest {

    @Mock
    private IndustrialProtocolBridgeService bridgeService;

    @InjectMocks
    private IndustrialBridgeController controller;

    @Test
    @DisplayName("ingestModbus: delegates to service and returns 202 Accepted")
    void ingestModbus_returnsAccepted() {
        ModbusIngressRequest req = new ModbusIngressRequest("plc-1", UUID.randomUUID(), 1, Collections.emptyList(), null, 1000L);
        TelemetryMessage msg = new TelemetryMessage();
        msg.setDeviceId("plc-1");
        when(bridgeService.ingestModbus(req)).thenReturn(msg);

        ResponseEntity<TelemetryMessage> res = controller.ingestModbus(req);

        assertThat(res.getStatusCode().value()).isEqualTo(202);
        assertThat(res.getBody()).isNotNull();
        assertThat(res.getBody().getDeviceId()).isEqualTo("plc-1");
        verify(bridgeService).ingestModbus(req);
    }

    @Test
    @DisplayName("ingestOpcUa: delegates to service and returns 202 Accepted")
    void ingestOpcUa_returnsAccepted() {
        OpcUaIngressRequest req = new OpcUaIngressRequest("opc-1", UUID.randomUUID(), "opc.tcp://localhost", Collections.emptyList(), 1000L);
        TelemetryMessage msg = new TelemetryMessage();
        msg.setDeviceId("opc-1");
        when(bridgeService.ingestOpcUa(req)).thenReturn(msg);

        ResponseEntity<TelemetryMessage> res = controller.ingestOpcUa(req);

        assertThat(res.getStatusCode().value()).isEqualTo(202);
        assertThat(res.getBody()).isNotNull();
        assertThat(res.getBody().getDeviceId()).isEqualTo("opc-1");
        verify(bridgeService).ingestOpcUa(req);
    }

    @Test
    @DisplayName("translateModbus: dry-run translation returns 200 OK")
    void translateModbus_returnsOk() {
        ModbusIngressRequest req = new ModbusIngressRequest("plc-1", UUID.randomUUID(), 1, Collections.emptyList(), null, 1000L);
        TelemetryMessage msg = new TelemetryMessage();
        msg.setDeviceId("plc-1");
        when(bridgeService.translateModbus(req)).thenReturn(msg);

        ResponseEntity<TelemetryMessage> res = controller.translateModbus(req);

        assertThat(res.getStatusCode().value()).isEqualTo(200);
        verify(bridgeService).translateModbus(req);
    }

    @Test
    @DisplayName("translateOpcUa: dry-run translation returns 200 OK")
    void translateOpcUa_returnsOk() {
        OpcUaIngressRequest req = new OpcUaIngressRequest("opc-1", UUID.randomUUID(), "opc.tcp://localhost", Collections.emptyList(), 1000L);
        TelemetryMessage msg = new TelemetryMessage();
        msg.setDeviceId("opc-1");
        when(bridgeService.translateOpcUa(req)).thenReturn(msg);

        ResponseEntity<TelemetryMessage> res = controller.translateOpcUa(req);

        assertThat(res.getStatusCode().value()).isEqualTo(200);
        verify(bridgeService).translateOpcUa(req);
    }
}
