package com.sentinel.iot.controller;

import com.sentinel.iot.dto.DeviceShadowDocument;
import com.sentinel.iot.dto.DeviceShadowUpdateRequest;
import com.sentinel.iot.security.TenantContext;
import com.sentinel.iot.service.AuditService;
import com.sentinel.iot.service.DeviceShadowService;
import com.sentinel.iot.util.HttpUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * REST API for Device Twin / Shadow state synchronization.
 * Exposes desired and reported state management, virtual delta inspection,
 * and optimistic concurrency control (OCC).
 */
@RestController
@RequestMapping("/api/v1/devices/{deviceId}/shadow")
@RequiredArgsConstructor
@Tag(name = "Device Twin / Shadow", description = "Device state abstraction, desired/reported synchronization and delta resolution")
public class DeviceShadowController {

    private final DeviceShadowService deviceShadowService;
    private final AuditService auditService;

    @GetMapping
    @Operation(summary = "Get full device shadow document",
               description = "Returns desired, reported, and virtual computed delta states with per-property metadata and OCC version.")
    public ResponseEntity<DeviceShadowDocument> getShadow(@PathVariable UUID deviceId) {
        UUID orgId = TenantContext.get();
        DeviceShadowDocument doc = deviceShadowService.getShadow(deviceId, orgId);
        return ResponseEntity.ok(doc);
    }

    @PatchMapping
    @Operation(summary = "Update device shadow desired or reported state",
               description = "Updates state with optimistic concurrency check. If desired state is changed, triggers delta push to the device.")
    public ResponseEntity<DeviceShadowDocument> updateShadow(
            @PathVariable UUID deviceId,
            @Valid @RequestBody DeviceShadowUpdateRequest request,
            Authentication authentication,
            HttpServletRequest httpRequest) {
        UUID orgId = TenantContext.get();
        DeviceShadowDocument doc = deviceShadowService.updateShadow(deviceId, orgId, request);

        if (authentication != null) {
            String clientIp = HttpUtils.resolveClientIp(httpRequest);
            auditService.log(authentication.getName(), "DEVICE_SHADOW_UPDATE",
                    "/api/v1/devices/" + deviceId + "/shadow",
                    "version=" + doc.version(), clientIp);
        }

        return ResponseEntity.ok(doc);
    }

    @DeleteMapping
    @Operation(summary = "Delete or reset device shadow document",
               description = "Removes persistent shadow document and clears Redis cache.")
    public ResponseEntity<Void> deleteShadow(
            @PathVariable UUID deviceId,
            Authentication authentication,
            HttpServletRequest httpRequest) {
        UUID orgId = TenantContext.get();
        deviceShadowService.deleteShadow(deviceId, orgId);

        if (authentication != null) {
            String clientIp = HttpUtils.resolveClientIp(httpRequest);
            auditService.log(authentication.getName(), "DEVICE_SHADOW_DELETE",
                    "/api/v1/devices/" + deviceId + "/shadow",
                    "deviceId=" + deviceId, clientIp);
        }

        return ResponseEntity.noContent().build();
    }
}
