package com.sentinel.iot;

import com.sentinel.iot.anomaly.engine.MultivariateAnomalyDetector;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

@Tag("unit")
@DisplayName("MultivariateAnomalyDetector (Phase 4)")
class MultivariateAnomalyDetectorTest {

    private MultivariateAnomalyDetector detector;

    @BeforeEach
    void setUp() {
        // Deterministic seed for reproducible tests
        detector = new MultivariateAnomalyDetector(42L, 64);
    }

    @Nested
    @DisplayName("Nominal Operational State")
    class NominalState {

        @Test
        @DisplayName("Nominal steady-state readings yield low anomaly score (< 0.50) and no anomaly")
        void nominalReadingsAreNotAnomalous() {
            // [zTemp, zHum, zSmoke, rocTemp, rocHum, thermalIndex]
            double[] nominal = new double[]{0.1, -0.1, 0.0, 0.05, -0.05, 0.0};
            MultivariateAnomalyDetector.InferenceResult result = detector.score(nominal);

            assertFalse(result.isAnomaly(), "Nominal readings should not be flagged as anomaly");
            assertTrue(result.anomalyScore() < 0.50, "Nominal score should be < 0.50 but was " + result.anomalyScore());
            assertEquals("NONE", result.anomalyType());
            assertEquals("INFO", result.severity());
            assertTrue(result.confidence() >= 0.95);
        }

        @Test
        @DisplayName("Zero vector (exactly on mean) produces baseline score")
        void zeroVectorNominal() {
            double[] zero = new double[]{0.0, 0.0, 0.0, 0.0, 0.0, 0.0};
            MultivariateAnomalyDetector.InferenceResult result = detector.score(zero);

            assertFalse(result.isAnomaly());
            assertTrue(result.anomalyScore() < 0.40);
        }
    }

    @Nested
    @DisplayName("Thermal Runaway Anomaly Detection")
    class ThermalRunaway {

        @Test
        @DisplayName("Thermal runaway pattern triggers CRITICAL anomaly with confidence > 95%")
        void thermalRunawayPatternDetected() {
            // High Temp (+3.5σ), Collapsing Humidity (-2.5σ), Smoke Surge (+4.0σ)
            double zTemp = 3.5;
            double zHum = -2.5;
            double zSmoke = 4.0;
            double thermalIndex = (Math.max(0, zTemp) * Math.max(0, -zHum)) + (1.5 * Math.max(0, zSmoke)); // 3.5*2.5 + 6.0 = 14.75

            double[] features = new double[]{zTemp, zHum, zSmoke, 1.2, -0.8, thermalIndex};
            MultivariateAnomalyDetector.InferenceResult result = detector.score(features);

            assertTrue(result.isAnomaly(), "Thermal runaway must be flagged as anomaly");
            assertEquals("THERMAL_RUNAWAY", result.anomalyType());
            assertEquals("CRITICAL", result.severity());
            assertTrue(result.anomalyScore() >= 0.88, "Anomaly score must be >= 0.88 but was " + result.anomalyScore());
            assertTrue(result.confidence() >= 0.95, "Confidence must be >= 95% but was " + result.confidence());

            // Feature contributions should reflect temperature and smoke as primary drivers
            Map<String, Double> contribs = result.featureContributions();
            assertNotNull(contribs);
            assertTrue(contribs.containsKey("temperature"));
            assertTrue(contribs.containsKey("smokePpm"));
            assertTrue(contribs.containsKey("thermalRunawayIndex"));
        }

        @Test
        @DisplayName("RFC-001 Acceptance Target: Precision > 95% on thermal runaway with FPR < 0.01% on nominal data")
        void verifyRfc001AcceptanceCriteria() {
            Random rng = new Random(12345);

            // 1. Evaluate 1,000 nominal cases (simulating standard factory operating envelope)
            int nominalCases = 1000;
            int falsePositives = 0;

            for (int i = 0; i < nominalCases; i++) {
                double zTemp = rng.nextGaussian() * 0.8;      // Standard deviation within normal range
                double zHum = rng.nextGaussian() * 0.8;
                double zSmoke = Math.max(-0.5, rng.nextGaussian() * 0.3);
                double rocTemp = rng.nextGaussian() * 0.2;
                double rocHum = rng.nextGaussian() * 0.2;
                double thermalIndex = Math.max(0, zTemp) * Math.max(0, -zHum) + 1.5 * Math.max(0, zSmoke);

                double[] sample = new double[]{zTemp, zHum, zSmoke, rocTemp, rocHum, thermalIndex};
                MultivariateAnomalyDetector.InferenceResult res = detector.score(sample);

                if ("THERMAL_RUNAWAY".equals(res.anomalyType())) {
                    falsePositives++;
                }
            }

            double fpr = (double) falsePositives / nominalCases;
            assertEquals(0, falsePositives, "False positive count on nominal data must be 0 (FPR < 0.01%)");
            assertTrue(fpr <= 0.0001, "False positive rate must be <= 0.01%");

            // 2. Evaluate 100 genuine thermal runaway cases
            int runawayCases = 100;
            int truePositives = 0;

            for (int i = 0; i < runawayCases; i++) {
                double zTemp = 2.5 + rng.nextDouble() * 2.5;     // 2.5 to 5.0 sigma
                double zHum = -1.5 - rng.nextDouble() * 2.0;    // -1.5 to -3.5 sigma
                double zSmoke = 2.0 + rng.nextDouble() * 3.0;   // 2.0 to 5.0 sigma
                double rocTemp = 1.0 + rng.nextDouble() * 1.5;
                double rocHum = -0.8 - rng.nextDouble() * 1.0;
                double thermalIndex = (zTemp * (-zHum)) + (1.5 * zSmoke);

                double[] sample = new double[]{zTemp, zHum, zSmoke, rocTemp, rocHum, thermalIndex};
                MultivariateAnomalyDetector.InferenceResult res = detector.score(sample);

                if ("THERMAL_RUNAWAY".equals(res.anomalyType())) {
                    truePositives++;
                }
            }

            double precision = (double) truePositives / (truePositives + falsePositives);
            assertTrue(precision >= 0.95, "Thermal runaway precision must be >= 95% (got " + (precision * 100) + "%)");
            assertEquals(100, truePositives, "All 100 thermal runaway cases should be correctly detected");
        }
    }

    @Nested
    @DisplayName("Feature Vector Builder")
    class FeatureVectorBuilder {

        @Test
        @DisplayName("Builds normalized z-score feature vector correctly from raw readings")
        void buildsFeatureVectorAccurately() {
            double[] vec = detector.buildFeatureVector(
                    35.0, 30.0, 6.0,
                    25.0, 40.0,
                    25.0, 5.0,   // temp mean=25, std=5 -> z = (35-25)/5 = +2.0
                    50.0, 10.0,  // hum mean=50, std=10 -> z = (30-50)/10 = -2.0
                    1.0, 1.0     // smoke mean=1, std=1 -> z = (6-1)/1 = +5.0
            );

            assertEquals(6, vec.length);
            assertEquals(2.0, vec[0], 0.001, "Temp z-score");
            assertEquals(-2.0, vec[1], 0.001, "Hum z-score");
            assertEquals(5.0, vec[2], 0.001, "Smoke z-score");
            assertEquals(2.0, vec[3], 0.001, "Temp rate of change (35-25)/5 = 2.0");
            assertEquals(-1.0, vec[4], 0.001, "Hum rate of change (30-40)/10 = -1.0");
            // thermalIndex = (2.0 * 2.0) + (1.5 * 5.0) = 4.0 + 7.5 = 11.5
            assertEquals(11.5, vec[5], 0.001, "Thermal Runaway Index");
        }
    }
}
