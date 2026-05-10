package com.sentinel.iot.repository;

import com.sentinel.iot.model.Telemetry;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Repository
public interface TelemetryRepository extends JpaRepository<Telemetry, UUID> {
    List<Telemetry> findByDeviceIdOrderByTimestampDesc(UUID deviceId, Pageable pageable);
    List<Telemetry> findByDeviceIdAndTimestampBetween(UUID deviceId, Instant from, Instant to);
    long countByTimestampAfter(Instant since);

    @org.springframework.data.jpa.repository.Modifying
    @org.springframework.data.jpa.repository.Query("DELETE FROM Telemetry t WHERE t.timestamp < :cutoff")
    int deleteByTimestampBefore(@org.springframework.data.repository.query.Param("cutoff") Instant cutoff);
}
