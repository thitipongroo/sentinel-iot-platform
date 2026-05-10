package com.sentinel.iot.controller;

import com.sentinel.iot.model.Alert;
import com.sentinel.iot.service.AlertService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/alerts")
@RequiredArgsConstructor
public class AlertController {

    private final AlertService alertService;

    @GetMapping
    public ResponseEntity<List<Alert>> getRecent() {
        return ResponseEntity.ok(alertService.getRecent());
    }

    @GetMapping("/unacknowledged")
    public ResponseEntity<List<Alert>> getUnacknowledged() {
        return ResponseEntity.ok(alertService.getUnacknowledged());
    }

    @PutMapping("/{id}/acknowledge")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> acknowledge(@PathVariable UUID id) {
        alertService.acknowledge(id);
        return ResponseEntity.noContent().build();
    }
}
