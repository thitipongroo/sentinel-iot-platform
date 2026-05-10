package com.sentinel.iot.repository;

import com.sentinel.iot.model.AuditLog;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface AuditLogRepository extends JpaRepository<AuditLog, UUID> {

    List<AuditLog> findByUsernameOrderByTimestampDesc(String username, PageRequest page);

    List<AuditLog> findTop100ByOrderByTimestampDesc();
}
