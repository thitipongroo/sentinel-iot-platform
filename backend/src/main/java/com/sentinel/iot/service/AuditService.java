package com.sentinel.iot.service;

import com.sentinel.iot.model.AuditLog;
import com.sentinel.iot.repository.AuditLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuditService {

    private final AuditLogRepository auditLogRepository;

    @Value("${audit.retention-days:90}")
    private int retentionDays;

    @Async
    public void log(String username, String action, String resource, String detail, String ipAddress) {
        try {
            auditLogRepository.save(new AuditLog(username, action, resource, detail, ipAddress));
            log.debug("AUDIT {} {} {} {}", username, action, resource, ipAddress);
        } catch (Exception e) {
            log.error("Failed to write audit log: {}", e.getMessage());
        }
    }

    @Scheduled(cron = "${audit.cron:0 30 3 * * *}")
    public void purgeOldAuditLogs() {
        Instant cutoff = Instant.now().minus(retentionDays, ChronoUnit.DAYS);
        int deleted = auditLogRepository.deleteOlderThan(cutoff);
        log.info("Purged {} audit log entries older than {} days", deleted, retentionDays);
    }
}
