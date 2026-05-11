package com.sentinel.iot.repository;

import com.sentinel.iot.model.DeviceEnrollmentToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface DeviceEnrollmentTokenRepository extends JpaRepository<DeviceEnrollmentToken, UUID> {

    Optional<DeviceEnrollmentToken> findByTokenHash(String tokenHash);

    @Modifying
    @Query("DELETE FROM DeviceEnrollmentToken t WHERE t.expiresAt < :cutoff")
    int deleteExpired(Instant cutoff);
}
