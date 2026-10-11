package com.sentinel.iot.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.integration.mqtt.support.MqttHeaders;
import org.springframework.integration.support.MessageBuilder;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.stereotype.Service;

/**
 * Service for publishing outbound MQTT messages (shadow updates, delta events, ACKs).
 */
@Service
@Slf4j
public class MqttPublisherService {

    private final MessageChannel mqttOutboundChannel;

    public MqttPublisherService(@Qualifier("mqttOutboundChannel") MessageChannel mqttOutboundChannel) {
        this.mqttOutboundChannel = mqttOutboundChannel;
    }

    /**
     * Publish a message with specified topic, payload, QoS, and retained flag.
     */
    public void publish(String topic, String payload, int qos, boolean retained) {
        try {
            Message<String> message = MessageBuilder.withPayload(payload)
                    .setHeader(MqttHeaders.TOPIC, topic)
                    .setHeader(MqttHeaders.QOS, qos)
                    .setHeader(MqttHeaders.RETAINED, retained)
                    .build();
            mqttOutboundChannel.send(message, 3000);
            log.debug("Published MQTT message to topic {} (qos={}, retained={})", topic, qos, retained);
        } catch (Exception e) {
            log.error("Failed to publish MQTT message to topic {}: {}", topic, e.getMessage());
        }
    }

    /**
     * Publish a message with default QoS 1 and retained = false.
     */
    public void publish(String topic, String payload) {
        publish(topic, payload, 1, false);
    }
}
