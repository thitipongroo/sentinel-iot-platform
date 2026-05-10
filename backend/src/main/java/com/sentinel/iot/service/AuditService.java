package com.sentinel.iot.service;

import com.sentinel.iot.model.AuditLog;
import com.sentinel.iot.repository.AuditLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuditService {

    private final AuditLogRepository auditLogRepository;

    @Async
    public void log(String username, String action, String resource, String detail, String ipAddress) {
        try {
            auditLogRepository.save(new AuditLog(username, action, resource, detail, ipAddress));
            log.debug("AUDIT {} {} {} {}", username, action, resource, ipAddress);
        } catch (Exception e) {
            log.error("Failed to write audit log: {}", e.getMessage());
        }
    }
}
