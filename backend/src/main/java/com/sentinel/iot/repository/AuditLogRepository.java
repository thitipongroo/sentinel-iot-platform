package com.sentinel.iot.repository;

import com.sentinel.iot.model.AuditLog;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface AuditLogRepository extends JpaRepository<AuditLog, UUID> {

    List<AuditLog> findByUsernameOrderByTimestampDesc(String username, PageRequest page);

    List<AuditLog> findTop100ByOrderByTimestampDesc();

    @Modifying
    @Transactional
    @Query("DELETE FROM AuditLog a WHERE a.timestamp < :cutoff")
    int deleteOlderThan(Instant cutoff);
}
