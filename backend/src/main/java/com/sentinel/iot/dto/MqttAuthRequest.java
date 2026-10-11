package com.sentinel.iot.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Request payload for MQTT broker authentication webhook (EMQX / Mosquitto).
 */
public record MqttAuthRequest(
        @JsonProperty("clientid")
        @JsonAlias({"clientId", "client_id"})
        String clientId,

        String username,
        String password
) {}
