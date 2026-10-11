package com.sentinel.iot.controller;

import com.sentinel.iot.dto.MqttAclRequest;
import com.sentinel.iot.dto.MqttAuthRequest;
import com.sentinel.iot.dto.MqttAuthResponse;
import com.sentinel.iot.service.MqttProvisioningService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Controller exposing dynamic MQTT authentication and topic authorization (ACL)
 * webhooks for modern MQTT brokers (EMQX HTTP Authenticator / Mosquitto auth plugins).
 */
@RestController
@RequestMapping({"/api/v1/auth/mqtt", "/api/v1/mqtt"})
@RequiredArgsConstructor
@Slf4j
@Tag(name = "MQTT Auth", description = "Dynamic authentication and ACL webhook endpoints for MQTT brokers")
public class MqttAuthController {

    private final MqttProvisioningService mqttProvisioningService;

    @PostMapping({"/authenticate", "/auth"})
    @Operation(summary = "Authenticate MQTT client connection (EMQX / Mosquitto webhook)")
    public ResponseEntity<MqttAuthResponse> authenticate(@RequestBody(required = false) MqttAuthRequest request) {
        if (request == null || request.username() == null || request.password() == null) {
            log.warn("MQTT authentication rejected: missing credentials");
            return ResponseEntity.ok(MqttAuthResponse.deny());
        }

        boolean valid = mqttProvisioningService.verifyCredentials(request.username(), request.password());
        log.debug("MQTT authentication evaluated for user={}: result={}", request.username(), valid ? "allow" : "deny");
        return ResponseEntity.ok(valid ? MqttAuthResponse.allow() : MqttAuthResponse.deny());
    }

    @PostMapping("/acl")
    @Operation(summary = "Authorize MQTT client topic publish/subscribe (EMQX / Mosquitto webhook)")
    public ResponseEntity<MqttAuthResponse> acl(@RequestBody(required = false) MqttAclRequest request) {
        if (request == null || request.username() == null || request.topic() == null) {
            log.warn("MQTT ACL rejected: missing username or topic");
            return ResponseEntity.ok(MqttAuthResponse.deny());
        }

        boolean allowed = mqttProvisioningService.verifyAcl(
                request.username(),
                request.clientId(),
                request.topic(),
                request.action()
        );
        log.debug("MQTT ACL evaluated for user={} topic={} action={}: result={}",
                request.username(), request.topic(), request.action(), allowed ? "allow" : "deny");
        return ResponseEntity.ok(allowed ? MqttAuthResponse.allow() : MqttAuthResponse.deny());
    }
}
