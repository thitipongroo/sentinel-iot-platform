package com.sentinel.iot;

import com.sentinel.iot.repository.AlertRepository;
import com.sentinel.iot.repository.DeviceRepository;
import com.sentinel.iot.service.BusinessMetricsService;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@Tag("unit")
@DisplayName("BusinessMetricsService")
@ExtendWith(MockitoExtension.class)
class BusinessMetricsServiceTest {

    @Mock DeviceRepository deviceRepository;
    @Mock AlertRepository  alertRepository;

    SimpleMeterRegistry    meterRegistry;
    BusinessMetricsService service;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        service = new BusinessMetricsService(deviceRepository, alertRepository, meterRegistry);
    }

    // ── Meter registration ────────────────────────────────────────────────────

    @Nested
    @DisplayName("Meter registration")
    class MeterRegistration {

        @Test
        @DisplayName("all four business meters are registered in the registry at construction time")
        void allBusinessMeters_areRegistered_atConstruction() {
            assertThat(meterRegistry.find("sentinel.business.active_devices").gauge())
                    .as("active_devices gauge").isNotNull();
            assertThat(meterRegistry.find("sentinel.business.total_devices").gauge())
                    .as("total_devices gauge").isNotNull();
            assertThat(meterRegistry.find("sentinel.business.unack_alerts").gauge())
                    .as("unack_alerts gauge").isNotNull();
            assertThat(meterRegistry.find("sentinel.business.alert_fired").counter())
                    .as("alert_fired counter").isNotNull();
        }
    }

    // ── Alert counter tracking ────────────────────────────────────────────────

    @Nested
    @DisplayName("Alert counter tracking")
    class AlertCounterTracking {

        @Test
        @DisplayName("recordAlertFired increments the alert_fired counter by 1 per call")
        void recordAlertFired_incrementsAlertFiredCounter() {
            service.recordAlertFired();
            service.recordAlertFired();

            Counter counter = meterRegistry.find("sentinel.business.alert_fired").counter();
            assertThat(counter).as("alert_fired counter exists").isNotNull();
            assertThat(counter.count()).as("counter value after 2 calls").isEqualTo(2.0);
        }
    }

    // ── Gauge refresh ─────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Gauge refresh")
    class GaugeRefresh {

        @SuppressWarnings("null")
        @Test
        @DisplayName("refresh reads all three gauge values from repositories and exposes them in the registry")
        void refresh_updatesAllGaugesFromRepositories() {
            when(deviceRepository.countByStatus("ONLINE")).thenReturn(42L);
            when(deviceRepository.count()).thenReturn(100L);
            when(alertRepository.countByAcknowledgedFalse()).thenReturn(7L);

            service.refresh();

            Gauge activeDevices = meterRegistry.find("sentinel.business.active_devices").gauge();
            Gauge totalDevices  = meterRegistry.find("sentinel.business.total_devices").gauge();
            Gauge unackAlerts   = meterRegistry.find("sentinel.business.unack_alerts").gauge();

            assertThat(activeDevices).as("active_devices gauge").isNotNull();
            assertThat(totalDevices).as("total_devices gauge").isNotNull();
            assertThat(unackAlerts).as("unack_alerts gauge").isNotNull();
            assertThat(activeDevices.value()).as("active device count").isEqualTo(42.0);
            assertThat(totalDevices.value()).as("total device count").isEqualTo(100.0);
            assertThat(unackAlerts.value()).as("unacknowledged alert count").isEqualTo(7.0);
        }

        @Test
        @DisplayName("refresh fails open — a repository exception must not propagate to the caller")
        void refresh_swallowsException_failOpen() {
            when(deviceRepository.countByStatus(anyString()))
                    .thenThrow(new RuntimeException("DB connection lost"));

            assertThatNoException()
                    .as("refresh() must be fail-open")
                    .isThrownBy(() -> service.refresh());
        }

        @SuppressWarnings("null")
        @Test
        @DisplayName("gauges reflect the latest repository values on each subsequent refresh call")
        void refresh_gaugesReflectUpdatedValues_onSubsequentCalls() {
            when(deviceRepository.countByStatus("ONLINE")).thenReturn(10L).thenReturn(20L);
            when(deviceRepository.count()).thenReturn(50L).thenReturn(60L);
            when(alertRepository.countByAcknowledgedFalse()).thenReturn(3L).thenReturn(0L);

            service.refresh();
            service.refresh();

            Gauge activeDevices = meterRegistry.find("sentinel.business.active_devices").gauge();
            assertThat(activeDevices).as("active_devices gauge").isNotNull();
            assertThat(activeDevices.value())
                    .as("gauge must reflect the second refresh value").isEqualTo(20.0);
        }
    }
}
