package com.sentinel.iot.benchmark;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.openjdk.jmh.results.RunResult;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;
import org.openjdk.jmh.runner.options.TimeValue;

import java.util.Collection;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Performance gate: runs JMH benchmarks and fails the build if any hot-path
 * operation exceeds its defined latency budget.
 *
 * Run with:  mvn test -Dgroups=performance
 *
 * Thresholds (conservative — any modern server should comfortably beat these):
 *   JWT generate  < 1 000 µs  (1 ms)
 *   JWT parse     < 1 000 µs  (1 ms)
 */
@Tag("performance")
@DisplayName("Performance gate — JWT hot paths")
class PerformanceGateTest {

    private static final double MAX_AVERAGE_MICROS = 1_000.0; // 1 ms

    @Nested
    @DisplayName("JWT operations")
    class JwtOperations {

        @Test
        @DisplayName("all JWT operations average < 1 000 µs (1 ms)")
        void jwtHotPaths_withinLatencyBudget() throws RunnerException {
            Options opts = new OptionsBuilder()
                    .include(JwtPerformanceBenchmark.class.getSimpleName())
                    .forks(1)
                    .warmupIterations(2)
                    .warmupTime(TimeValue.seconds(1))
                    .measurementIterations(3)
                    .measurementTime(TimeValue.seconds(1))
                    .timeUnit(TimeUnit.MICROSECONDS)
                    .shouldFailOnError(true)
                    .build();

            Collection<RunResult> results = new Runner(opts).run();

            assertThat(results)
                    .as("JMH runner must produce at least one benchmark result")
                    .isNotEmpty();

            for (RunResult result : results) {
                double avgMicros = result.getPrimaryResult().getScore();
                String benchmarkName = result.getParams().getBenchmark();

                assertThat(avgMicros)
                        .as("'%s' avg=%.1f µs must be < %.0f µs",
                                benchmarkName, avgMicros, MAX_AVERAGE_MICROS)
                        .isLessThan(MAX_AVERAGE_MICROS);
            }
        }
    }
}
