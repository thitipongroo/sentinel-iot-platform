package com.sentinel.iot.service;

import com.sentinel.iot.model.AuditLog;
import com.sentinel.iot.repository.AuditLogRepository;
import com.sentinel.iot.security.TenantContext;
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

    @Async("auditExecutor")
    public void log(String username, String action, String resource, String detail, String ipAddress) {
        try {
            // TenantContext is propagated into this thread by auditExecutor's TaskDecorator
            AuditLog entry = new AuditLog(username, action, resource, detail, ipAddress);
            entry.setOrganizationId(TenantContext.get());
            auditLogRepository.save(entry);
            log.debug("AUDIT {} {} {} {}", username, action, resource, ipAddress);
        } catch (Exception e) {
            log.error("Failed to write audit log: {}", e.getMessage());
        }
    }

    public org.springframework.data.domain.Page<AuditLog> getLogs(
            String username, String action, Instant from, Instant to, int page, int size) {
        org.springframework.data.domain.Pageable pageable =
                org.springframework.data.domain.PageRequest.of(page, size, org.springframework.data.domain.Sort.by("timestamp").descending());
        java.util.UUID orgId = TenantContext.get();
        return auditLogRepository.searchLogs(orgId, username, action, from, to, pageable);
    }

    @Scheduled(cron = "${audit.cron:0 30 3 * * *}")
    public void purgeOldAuditLogs() {
        Instant cutoff = Instant.now().minus(retentionDays, ChronoUnit.DAYS);
        int deleted = auditLogRepository.deleteOlderThan(cutoff);
        log.info("Purged {} audit log entries older than {} days", deleted, retentionDays);
    }
}
