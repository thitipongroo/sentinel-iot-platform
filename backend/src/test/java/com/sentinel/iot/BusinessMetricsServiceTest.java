package com.sentinel.iot;

import com.sentinel.iot.repository.AlertRepository;
import com.sentinel.iot.repository.DeviceRepository;
import com.sentinel.iot.service.BusinessMetricsService;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BusinessMetricsServiceTest {

    @Mock DeviceRepository deviceRepository;
    @Mock AlertRepository alertRepository;

    SimpleMeterRegistry meterRegistry;
    BusinessMetricsService service;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        service = new BusinessMetricsService(deviceRepository, alertRepository, meterRegistry);
    }

    @Test
    void metersAreRegistered_atConstruction() {
        assertThat(meterRegistry.find("sentinel.business.active_devices").gauge()).isNotNull();
        assertThat(meterRegistry.find("sentinel.business.total_devices").gauge()).isNotNull();
        assertThat(meterRegistry.find("sentinel.business.unack_alerts").gauge()).isNotNull();
        assertThat(meterRegistry.find("sentinel.business.alert_fired").counter()).isNotNull();
    }

    @Test
    void recordAlertFired_incrementsAlertFiredCounter() {
        service.recordAlertFired();
        service.recordAlertFired();

        Counter counter = meterRegistry.find("sentinel.business.alert_fired").counter();
        assertThat(counter).isNotNull();
        assertThat(counter.count()).isEqualTo(2.0);
    }

    @SuppressWarnings("null")
    @Test
    void refresh_updatesAllGaugesFromRepositories() {
        when(deviceRepository.countByStatus("ONLINE")).thenReturn(42L);
        when(deviceRepository.count()).thenReturn(100L);
        when(alertRepository.countByAcknowledgedFalse()).thenReturn(7L);

        service.refresh();

        Gauge activeDevices = meterRegistry.find("sentinel.business.active_devices").gauge();
        Gauge totalDevices  = meterRegistry.find("sentinel.business.total_devices").gauge();
        Gauge unackAlerts   = meterRegistry.find("sentinel.business.unack_alerts").gauge();
        assertThat(activeDevices).isNotNull();
        assertThat(totalDevices).isNotNull();
        assertThat(unackAlerts).isNotNull();
        assertThat(activeDevices.value()).isEqualTo(42.0);
        assertThat(totalDevices.value()).isEqualTo(100.0);
        assertThat(unackAlerts.value()).isEqualTo(7.0);
    }

    @Test
    void refresh_swallowsException_failOpen() {
        when(deviceRepository.countByStatus(anyString()))
                .thenThrow(new RuntimeException("DB connection lost"));

        assertThatNoException().isThrownBy(() -> service.refresh());
    }

    @SuppressWarnings("null")
    @Test
    void refresh_gaugesReflectUpdatedValues_onSubsequentCalls() {
        when(deviceRepository.countByStatus("ONLINE")).thenReturn(10L).thenReturn(20L);
        when(deviceRepository.count()).thenReturn(50L).thenReturn(60L);
        when(alertRepository.countByAcknowledgedFalse()).thenReturn(3L).thenReturn(0L);

        service.refresh();
        service.refresh();

        Gauge activeDevices = meterRegistry.find("sentinel.business.active_devices").gauge();
        assertThat(activeDevices).isNotNull();
        assertThat(activeDevices.value()).isEqualTo(20.0);
    }
}
