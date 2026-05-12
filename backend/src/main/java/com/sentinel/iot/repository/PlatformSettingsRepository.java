package com.sentinel.iot.repository;

import com.sentinel.iot.model.PlatformSettings;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface PlatformSettingsRepository extends JpaRepository<PlatformSettings, UUID> {}
