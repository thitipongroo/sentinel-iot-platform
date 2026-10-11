package com.sentinel.iot;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.sentinel.iot.anomaly.dto.AdaptiveThresholdConfig;
import com.sentinel.iot.anomaly.dto.DynamicBaseline;
import com.sentinel.iot.anomaly.service.AdaptiveThresholdService;
import com.sentinel.iot.security.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@Tag("unit")
@DisplayName("AdaptiveThresholdService (Phase 4)")
@ExtendWith(MockitoExtension.class)
class AdaptiveThresholdServiceTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    private ObjectMapper objectMapper;
    private AdaptiveThresholdService thresholdService;

    private final UUID orgId = UUID.fromString("aaaaaaaa-0000-0000-0000-000000000001");
    private final UUID deviceId = UUID.fromString("dddddddd-0000-0000-0000-000000000001");

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        thresholdService = new AdaptiveThresholdService(redisTemplate, objectMapper);
        ReflectionTestUtils.setField(thresholdService, "minSigmaFloor", 0.2);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Nested
    @DisplayName("Baseline Learning & Convergence")
    class BaselineLearning {

        @Test
        @DisplayName("First observation initializes baseline correctly")
        void initializesBaselineOnFirstObservation() {
            DynamicBaseline baseline = thresholdService.updateAndEvaluate(orgId, deviceId, "TEMPERATURE", 25.0);

            assertNotNull(baseline);
            assertEquals("TEMPERATURE", baseline.getSensorKey());
            assertEquals(25.0, baseline.getMean());
            assertEquals(1L, baseline.getSampleCount());
            assertFalse(baseline.isBreached(), "First observation should not breach");

            verify(valueOperations, atLeastOnce()).set(contains(deviceId.toString()), anyString(), any());
        }

        @Test
        @DisplayName("Warmup period prevents false breaches during initial 10 samples")
        void warmupPreventsPrematureBreaches() {
            AdaptiveThresholdConfig config = AdaptiveThresholdConfig.builder()
                    .minSamples(5)
                    .alpha(0.1)
                    .sigmaMultiplier(3.0)
                    .build();

            // Feed 3 samples with jitter
            thresholdService.updateAndEvaluate(orgId, deviceId, "TEMPERATURE", 20.0, config);
            thresholdService.updateAndEvaluate(orgId, deviceId, "TEMPERATURE", 21.0, config);
            DynamicBaseline b3 = thresholdService.updateAndEvaluate(orgId, deviceId, "TEMPERATURE", 28.0, config);

            assertEquals(3L, b3.getSampleCount());
            assertFalse(b3.isBreached(), "Should not breach during warmup (samples < 5)");
        }

        @Test
        @DisplayName("Detects breach when value deviates beyond μ ± 3σ after warmup")
        void detectsDynamicBreachAfterWarmup() {
            AdaptiveThresholdConfig config = AdaptiveThresholdConfig.builder()
                    .minSamples(5)
                    .alpha(0.05)
                    .sigmaMultiplier(3.0)
                    .build();

            // Train baseline with 10 stable readings around 25.0
            for (int i = 0; i < 10; i++) {
                thresholdService.updateAndEvaluate(orgId, deviceId, "TEMPERATURE", 25.0 + (i % 2 == 0 ? 0.2 : -0.2), config);
            }

            // Sudden surge to 45.0
            DynamicBaseline surge = thresholdService.updateAndEvaluate(orgId, deviceId, "TEMPERATURE", 45.0, config);

            assertTrue(surge.isBreached(), "Surge to 45.0 should trigger dynamic threshold breach");
            assertTrue(surge.getZScore() > 3.0, "z-score should be > 3.0 but was " + surge.getZScore());
            assertTrue(surge.getLastObservedValue() > surge.getUpperBand());
        }

        @Test
        @DisplayName("Extreme anomaly downweights baseline learning rate to prevent baseline poisoning")
        void dampensOutlierToPreventPoisoning() {
            AdaptiveThresholdConfig config = AdaptiveThresholdConfig.builder()
                    .minSamples(5)
                    .alpha(0.1)
                    .anomalyDampeningFactor(0.05) // Damped 20x
                    .sigmaMultiplier(3.0)
                    .build();

            // Establish baseline at 25.0
            for (int i = 0; i < 10; i++) {
                thresholdService.updateAndEvaluate(orgId, deviceId, "TEMPERATURE", 25.0, config);
            }

            // Extreme outlier at 100.0 (z-score > 10)
            DynamicBaseline afterSpike = thresholdService.updateAndEvaluate(orgId, deviceId, "TEMPERATURE", 100.0, config);

            // Because of dampening, the mean should not jump to 32.5 (standard alpha 0.1 * 75 = 7.5),
            // but instead should stay very close to 25.0 (alpha * 0.05 = 0.005 * 75 = ~0.375 -> mean ~25.4)
            assertTrue(afterSpike.getMean() < 26.5, "Mean should stay protected near 25.0, but was " + afterSpike.getMean());
            assertTrue(afterSpike.isBreached());
        }
    }

    @Nested
    @DisplayName("Reset & Multi-Tenancy")
    class ResetAndMultiTenancy {

        @Test
        @DisplayName("Resets all baselines for a device in local cache and Redis")
        void resetsDeviceBaselines() {
            thresholdService.updateAndEvaluate(orgId, deviceId, "TEMPERATURE", 25.0);
            thresholdService.updateAndEvaluate(orgId, deviceId, "HUMIDITY", 50.0);

            when(redisTemplate.keys(anyString())).thenReturn(Set.of("sentinel:baseline:" + orgId + ":" + deviceId + ":TEMPERATURE"));

            thresholdService.resetBaselines(orgId, deviceId);

            Optional<DynamicBaseline> temp = thresholdService.getBaseline(orgId, deviceId, "TEMPERATURE");
            assertFalse(temp.isPresent());
            verify(redisTemplate, atLeastOnce()).delete(any(Set.class));
        }

        @Test
        @DisplayName("Resets a single sensor baseline without affecting other sensors")
        void resetsSingleSensorBaseline() {
            thresholdService.updateAndEvaluate(orgId, deviceId, "TEMPERATURE", 25.0);
            thresholdService.updateAndEvaluate(orgId, deviceId, "HUMIDITY", 50.0);

            thresholdService.resetBaseline(orgId, deviceId, "TEMPERATURE");

            Optional<DynamicBaseline> temp = thresholdService.getBaseline(orgId, deviceId, "TEMPERATURE");
            Optional<DynamicBaseline> hum = thresholdService.getBaseline(orgId, deviceId, "HUMIDITY");

            assertFalse(temp.isPresent());
            assertTrue(hum.isPresent(), "Humidity baseline should remain intact");
        }

        @Test
        @DisplayName("Baselines are isolated across different tenants")
        void isolatesBaselinesPerTenant() {
            UUID tenantA = UUID.randomUUID();
            UUID tenantB = UUID.randomUUID();

            thresholdService.updateAndEvaluate(tenantA, deviceId, "TEMPERATURE", 20.0);
            thresholdService.updateAndEvaluate(tenantB, deviceId, "TEMPERATURE", 40.0);

            DynamicBaseline baseA = thresholdService.getBaseline(tenantA, deviceId, "TEMPERATURE").orElseThrow();
            DynamicBaseline baseB = thresholdService.getBaseline(tenantB, deviceId, "TEMPERATURE").orElseThrow();

            assertEquals(20.0, baseA.getMean());
            assertEquals(40.0, baseB.getMean());
        }
    }
}
