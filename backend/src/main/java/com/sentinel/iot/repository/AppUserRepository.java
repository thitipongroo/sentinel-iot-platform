package com.sentinel.iot.repository;

import com.sentinel.iot.model.AppUser;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface AppUserRepository extends JpaRepository<AppUser, UUID> {
    Optional<AppUser> findByUsername(String username);

    java.util.List<AppUser> findAllByOrganizationId(java.util.UUID organizationId);
}
