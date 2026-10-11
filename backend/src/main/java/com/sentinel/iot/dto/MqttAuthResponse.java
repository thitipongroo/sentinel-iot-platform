package com.sentinel.iot.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Response payload for MQTT broker authentication and ACL webhooks (EMQX / Mosquitto).
 */
public record MqttAuthResponse(
        String result,
        @JsonProperty("is_superuser")
        boolean isSuperuser
) {
    public static MqttAuthResponse allow() {
        return new MqttAuthResponse("allow", false);
    }

    public static MqttAuthResponse deny() {
        return new MqttAuthResponse("deny", false);
    }
}
