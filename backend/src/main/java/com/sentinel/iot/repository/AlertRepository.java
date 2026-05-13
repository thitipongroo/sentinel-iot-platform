package com.sentinel.iot.repository;

import com.sentinel.iot.model.Alert;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface AlertRepository extends JpaRepository<Alert, UUID> {
    List<Alert> findByDeviceIdOrderByCreatedAtDesc(UUID deviceId);
    List<Alert> findByAcknowledgedFalseOrderByCreatedAtDesc();
    Page<Alert> findAllByOrderByCreatedAtDesc(Pageable pageable);
    long countByAcknowledgedFalse();

    @org.springframework.data.jpa.repository.Modifying
    @org.springframework.data.jpa.repository.Query("UPDATE Alert a SET a.acknowledged = true WHERE a.acknowledged = false")
    int acknowledgeAll();
}
