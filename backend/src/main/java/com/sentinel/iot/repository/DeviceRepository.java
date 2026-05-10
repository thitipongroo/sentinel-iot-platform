package com.sentinel.iot.repository;

import com.sentinel.iot.model.Device;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface DeviceRepository extends JpaRepository<Device, UUID> {
    Optional<Device> findByName(String name);
    boolean existsByName(String name);

    List<Device> findAllByOrganizationId(UUID organizationId);
    Optional<Device> findByIdAndOrganizationId(UUID id, UUID organizationId);
    boolean existsByNameAndOrganizationId(String name, UUID organizationId);
}
