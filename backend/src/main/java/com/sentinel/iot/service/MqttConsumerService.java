package com.sentinel.iot.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sentinel.iot.dto.TelemetryMessage;
import com.sentinel.iot.model.Device;
import com.sentinel.iot.repository.DeviceRepository;
import com.sentinel.iot.websocket.TelemetryWebSocketHandler;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.integration.annotation.ServiceActivator;
import org.springframework.messaging.Message;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Service
@Slf4j
public class MqttConsumerService {

    private final TelemetryService telemetryService;
    private final AlertService alertService;
    private final DeviceRepository deviceRepository;
    private final TelemetryWebSocketHandler webSocketHandler;
    private final ObjectMapper objectMapper;
    private final Counter mqttCounter;

    public MqttConsumerService(TelemetryService telemetryService,
                               AlertService alertService,
                               DeviceRepository deviceRepository,
                               TelemetryWebSocketHandler webSocketHandler,
                               MeterRegistry meterRegistry) {
        this.telemetryService = telemetryService;
        this.alertService = alertService;
        this.deviceRepository = deviceRepository;
        this.webSocketHandler = webSocketHandler;
        this.objectMapper = new ObjectMapper();
        this.mqttCounter = Counter.builder("sentinel.mqtt.messages")
                .description("Total MQTT messages processed")
                .register(meterRegistry);
    }

    @ServiceActivator(inputChannel = "mqttInputChannel")
    public void handleMessage(Message<?> message) {
        try {
            String payload = message.getPayload().toString();
            TelemetryMessage msg = objectMapper.readValue(payload, TelemetryMessage.class);

            Optional<Device> deviceOpt = deviceRepository.findByName(msg.getDeviceId());
            if (deviceOpt.isEmpty()) {
                log.warn("Received telemetry for unknown device: {}", msg.getDeviceId());
                return;
            }

            Device device = deviceOpt.get();
            UUID deviceId = device.getId();

            device.setStatus("ONLINE");
            device.setLastSeen(Instant.now());
            deviceRepository.save(device);

            telemetryService.save(deviceId, msg.getTemperature(), msg.getHumidity());
            alertService.evaluate(deviceId, device.getName(), msg.getTemperature(), msg.getHumidity());

            webSocketHandler.broadcast(payload);
            mqttCounter.increment();

            log.debug("Processed telemetry from {}: temp={} humidity={}", msg.getDeviceId(), msg.getTemperature(), msg.getHumidity());
        } catch (Exception e) {
            log.error("Failed to process MQTT message: {}", e.getMessage());
        }
    }
}
