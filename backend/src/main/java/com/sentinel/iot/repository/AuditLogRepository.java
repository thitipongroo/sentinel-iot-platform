package com.sentinel.iot.repository;

import com.sentinel.iot.model.AuditLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface AuditLogRepository extends JpaRepository<AuditLog, UUID> {

    List<AuditLog> findByUsernameOrderByTimestampDesc(String username, PageRequest page);

    List<AuditLog> findTop100ByOrderByTimestampDesc();

    Page<AuditLog> findAllByOrderByTimestampDesc(Pageable pageable);

    Page<AuditLog> findByOrganizationIdOrderByTimestampDesc(UUID organizationId, Pageable pageable);

    @Query("SELECT a FROM AuditLog a WHERE " +
           "(:orgId IS NULL OR a.organizationId = :orgId) AND " +
           "(:username IS NULL OR LOWER(a.username) LIKE LOWER(CONCAT('%', :username, '%'))) AND " +
           "(:action IS NULL OR a.action = :action) AND " +
           "(:from IS NULL OR a.timestamp >= :from) AND " +
           "(:to IS NULL OR a.timestamp <= :to)")
    Page<AuditLog> searchLogs(@Param("orgId") UUID orgId,
                              @Param("username") String username,
                              @Param("action") String action,
                              @Param("from") Instant from,
                              @Param("to") Instant to,
                              Pageable pageable);

    @Modifying
    @Transactional
    @Query("DELETE FROM AuditLog a WHERE a.timestamp < :cutoff")
    int deleteOlderThan(Instant cutoff);
}
