package com.sentinel.iot.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Request payload for MQTT broker authorization (ACL) webhook (EMQX / Mosquitto).
 */
public record MqttAclRequest(
        @JsonProperty("clientid")
        @JsonAlias({"clientId", "client_id"})
        String clientId,

        String username,
        String topic,
        String action
) {}
