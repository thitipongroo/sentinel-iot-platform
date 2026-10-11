package com.sentinel.iot.repository;

import com.sentinel.iot.model.DeviceShadow;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface DeviceShadowRepository extends JpaRepository<DeviceShadow, UUID> {
    Optional<DeviceShadow> findByDeviceIdAndOrganizationId(UUID deviceId, UUID organizationId);
    void deleteByDeviceIdAndOrganizationId(UUID deviceId, UUID organizationId);
}
