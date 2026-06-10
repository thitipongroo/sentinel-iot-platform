package com.sentinel.iot.concurrent;

import com.sentinel.iot.BaseIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

/**
 * Concurrency tests — verify the application handles concurrent in-process requests
 * correctly without exceptions, deadlocks, or data races.
 *
 * Each request uses a unique fake IP (via MockMvc's remoteAddr override) so
 * the rate-limiter's per-IP buckets do not interfere across concurrent requests.
 *
 * Run with:  mvn test -Dgroups=load
 */
@Tag("load")
@DisplayName("ConcurrentLoadTest — concurrent request handling under load")
class ConcurrentLoadTest extends BaseIntegrationTest {

    // ── Read endpoint under load ──────────────────────────────────────────────

    @Nested
    @DisplayName("Device list endpoint")
    class DeviceListEndpoint {

        @Test
        @DisplayName("50 concurrent GET /api/v1/devices requests produce 0 errors and 0 exceptions")
        void deviceList_50ConcurrentRequests_zeroErrors() throws Exception {
            String token = loginAndGetToken("admin", "admin123");

            int concurrency = 50;
            ExecutorService executor  = Executors.newFixedThreadPool(10);
            CountDownLatch  latch     = new CountDownLatch(concurrency);
            AtomicInteger   successes = new AtomicInteger(0);
            AtomicInteger   errors    = new AtomicInteger(0);

            for (int i = 0; i < concurrency; i++) {
                final String fakeIp = "10.20.30." + (i + 1); // unique per-request IP
                executor.submit(() -> {
                    try {
                        int status = mockMvc.perform(get("/api/v1/devices")
                                .with(req -> { req.setRemoteAddr(fakeIp); return req; })
                                .header("Authorization", "Bearer " + token))
                                .andReturn()
                                .getResponse()
                                .getStatus();
                        if (status == 200) successes.incrementAndGet();
                        else              errors.incrementAndGet();
                    } catch (Exception e) {
                        errors.incrementAndGet();
                    } finally {
                        latch.countDown();
                    }
                });
            }

            boolean completed = latch.await(30, TimeUnit.SECONDS);

            assertThat(completed)
                    .as("all 50 requests must complete within 30 seconds")
                    .isTrue();
            assertThat(errors.get())
                    .as("concurrent GET /api/v1/devices must produce 0 non-200 responses or exceptions")
                    .isZero();
            assertThat(successes.get())
                    .as("all 50 concurrent requests must succeed with HTTP 200")
                    .isEqualTo(50);

            executor.shutdownNow();
        }
    }

    // ── Auth endpoint under load ──────────────────────────────────────────────

    @Nested
    @DisplayName("Auth login endpoint")
    class AuthLoginEndpoint {

        @SuppressWarnings("null")
        @Test
        @DisplayName("20 concurrent POST /api/v1/auth/login requests produce 0 exceptions")
        void login_20ConcurrentRequests_zeroExceptions() throws Exception {
            int concurrency = 20;
            ExecutorService executor   = Executors.newFixedThreadPool(10);
            CountDownLatch  latch      = new CountDownLatch(concurrency);
            AtomicInteger   exceptions = new AtomicInteger(0);
            AtomicInteger   successes  = new AtomicInteger(0);

            for (int i = 0; i < concurrency; i++) {
                final String fakeIp = "10.30.40." + (i + 1);
                executor.submit(() -> {
                    try {
                        int status = mockMvc.perform(post("/api/v1/auth/login")
                                .with(req -> { req.setRemoteAddr(fakeIp); return req; })
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(
                                        authRequest("admin", "admin123"))))
                                .andReturn()
                                .getResponse()
                                .getStatus();
                        if (status == 200) successes.incrementAndGet();
                    } catch (Exception e) {
                        exceptions.incrementAndGet();
                    } finally {
                        latch.countDown();
                    }
                });
            }

            boolean completed = latch.await(30, TimeUnit.SECONDS);

            assertThat(completed)
                    .as("all 20 login requests must complete within 30 seconds")
                    .isTrue();
            assertThat(exceptions.get())
                    .as("concurrent logins must produce 0 exceptions")
                    .isZero();
            assertThat(successes.get())
                    .as("all 20 concurrent logins must return HTTP 200")
                    .isEqualTo(20);

            executor.shutdownNow();
        }
    }

    // ── Mixed read/write load ─────────────────────────────────────────────────

    @Nested
    @DisplayName("Mixed read/write load")
    class MixedLoad {

        @SuppressWarnings("null")
        @Test
        @DisplayName("30 concurrent reads + 10 concurrent writes complete with 0 exceptions")
        void mixedReadWrite_noExceptions() throws Exception {
            String token = loginAndGetToken("admin", "admin123");

            int reads  = 30;
            int writes = 10;
            int total  = reads + writes;

            ExecutorService executor   = Executors.newFixedThreadPool(20);
            CountDownLatch  latch      = new CountDownLatch(total);
            AtomicInteger   exceptions = new AtomicInteger(0);

            // Read tasks
            for (int i = 0; i < reads; i++) {
                final String fakeIp = "10.40.50." + (i + 1);
                executor.submit(() -> {
                    try {
                        mockMvc.perform(get("/api/v1/devices")
                                .with(req -> { req.setRemoteAddr(fakeIp); return req; })
                                .header("Authorization", "Bearer " + token))
                                .andReturn();
                    } catch (Exception e) {
                        exceptions.incrementAndGet();
                    } finally {
                        latch.countDown();
                    }
                });
            }

            // Write tasks (create device)
            for (int i = 0; i < writes; i++) {
                final String deviceName = "load-test-" + System.nanoTime() + "-" + i;
                final String fakeIp    = "10.40.60." + (i + 1);
                executor.submit(() -> {
                    try {
                        mockMvc.perform(post("/api/v1/devices")
                                .with(req -> { req.setRemoteAddr(fakeIp); return req; })
                                .header("Authorization", "Bearer " + token)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(
                                        java.util.Map.of("name", deviceName))))
                                .andReturn();
                    } catch (Exception e) {
                        exceptions.incrementAndGet();
                    } finally {
                        latch.countDown();
                    }
                });
            }

            boolean completed = latch.await(60, TimeUnit.SECONDS);

            assertThat(completed)
                    .as("all 40 mixed read/write requests must complete within 60 seconds")
                    .isTrue();
            assertThat(exceptions.get())
                    .as("mixed concurrent read/write must produce 0 exceptions")
                    .isZero();

            executor.shutdownNow();
        }
    }
}
