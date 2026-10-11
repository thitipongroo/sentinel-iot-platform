package com.sentinel.iot.repository;

import com.sentinel.iot.model.Alert;
import com.sentinel.iot.model.AlertState;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Repository
public interface AlertRepository extends JpaRepository<Alert, UUID> {
    List<Alert> findByDeviceIdOrderByCreatedAtDesc(UUID deviceId);
    List<Alert> findByAcknowledgedFalseOrderByCreatedAtDesc();
    Page<Alert> findAllByOrderByCreatedAtDesc(Pageable pageable);
    Page<Alert> findByStateOrderByCreatedAtDesc(AlertState state, Pageable pageable);
    List<Alert> findByDeviceIdAndState(UUID deviceId, AlertState state);
    boolean existsByDeviceIdAndStateAndSilencedUntilAfter(UUID deviceId, AlertState state, Instant now);
    long countByAcknowledgedFalse();

    @Modifying
    @Query("UPDATE Alert a SET a.acknowledged = true, a.state = com.sentinel.iot.model.AlertState.ACKNOWLEDGED, a.acknowledgedAt = CURRENT_TIMESTAMP WHERE a.acknowledged = false OR a.state = com.sentinel.iot.model.AlertState.FIRING")
    int acknowledgeAll();
}
