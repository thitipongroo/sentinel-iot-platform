package com.sentinel.iot.repository;

import com.sentinel.iot.model.Alert;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface AlertRepository extends JpaRepository<Alert, UUID> {
    List<Alert> findByDeviceIdOrderByCreatedAtDesc(UUID deviceId);
    List<Alert> findByAcknowledgedFalseOrderByCreatedAtDesc();
    List<Alert> findTop50ByOrderByCreatedAtDesc();
    long countByAcknowledgedFalse();
}
