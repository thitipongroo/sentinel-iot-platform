package com.sentinel.iot.contract;

import au.com.dius.pact.consumer.MockServer;
import au.com.dius.pact.consumer.dsl.PactDslJsonBody;
import au.com.dius.pact.consumer.dsl.PactDslWithProvider;
import au.com.dius.pact.consumer.junit5.PactConsumerTestExt;
import au.com.dius.pact.consumer.junit5.PactTestFor;
import au.com.dius.pact.core.model.RequestResponsePact;
import au.com.dius.pact.core.model.annotations.Pact;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.http.*;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Consumer-driven contract tests (Pact) for sentinel-iot-backend.
 *
 * These tests define what "sentinel-frontend" expects from the API.
 * Pact writes contract files to target/pacts/ for provider-side verification.
 *
 * Provider verification:
 *   mvn test -Dgroups=contract   (consumer side — this file)
 *   mvn test -Dgroups=pact-verify (provider side — verifies target/pacts/*.json)
 */
@Tag("contract")
@DisplayName("Sentinel API — consumer contract (Pact)")
@ExtendWith(PactConsumerTestExt.class)
@PactTestFor(providerName = "sentinel-iot-backend")
class SentinelApiConsumerContractTest {

    // ── Auth API ──────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Auth API contract")
    @PactTestFor(providerName = "sentinel-iot-backend")
    @ExtendWith(PactConsumerTestExt.class)
    class AuthApiContract {

        @Pact(consumer = "sentinel-frontend")
        RequestResponsePact loginSuccessPact(PactDslWithProvider builder) {
            return builder
                    .given("admin user exists")
                    .uponReceiving("POST /auth/login with valid credentials")
                    .path("/api/v1/auth/login")
                    .method("POST")
                    .headers(Map.of("Content-Type", "application/json"))
                    .body(new PactDslJsonBody()
                            .stringValue("username", "admin")
                            .stringValue("password", "admin123"))
                    .willRespondWith()
                    .status(200)
                    .headers(Map.of("Content-Type", "application/json"))
                    .body(new PactDslJsonBody()
                            .stringType("accessToken")
                            .stringType("role")
                            .stringType("username"))
                    .toPact();
        }

        @Test
        @PactTestFor(pactMethod = "loginSuccessPact")
        @DisplayName("valid credentials → 200 with accessToken, role, username")
        void loginSuccess_contract(MockServer mockServer) {
            RestTemplate rest = noThrowRestTemplate();
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            ResponseEntity<String> response = rest.exchange(
                    mockServer.getUrl() + "/api/v1/auth/login",
                    HttpMethod.POST,
                    new HttpEntity<>("{\"username\":\"admin\",\"password\":\"admin123\"}", headers),
                    String.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).contains("accessToken");
            assertThat(response.getBody()).contains("role");
            assertThat(response.getBody()).contains("username");
        }

        @Pact(consumer = "sentinel-frontend")
        RequestResponsePact loginFailurePact(PactDslWithProvider builder) {
            return builder
                    .given("admin user exists")
                    .uponReceiving("POST /auth/login with wrong password")
                    .path("/api/v1/auth/login")
                    .method("POST")
                    .headers(Map.of("Content-Type", "application/json"))
                    .body(new PactDslJsonBody()
                            .stringValue("username", "admin")
                            .stringValue("password", "wrong-password"))
                    .willRespondWith()
                    .status(401)
                    .toPact();
        }

        @Test
        @PactTestFor(pactMethod = "loginFailurePact")
        @DisplayName("wrong password → 401 Unauthorized")
        void loginFailure_contract(MockServer mockServer) {
            RestTemplate rest = noThrowRestTemplate();
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            ResponseEntity<String> response = rest.exchange(
                    mockServer.getUrl() + "/api/v1/auth/login",
                    HttpMethod.POST,
                    new HttpEntity<>("{\"username\":\"admin\",\"password\":\"wrong-password\"}", headers),
                    String.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        }
    }

    // ── Device API ────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Device API contract")
    @PactTestFor(providerName = "sentinel-iot-backend")
    @ExtendWith(PactConsumerTestExt.class)
    class DeviceApiContract {

        @Pact(consumer = "sentinel-frontend")
        RequestResponsePact deviceListPact(PactDslWithProvider builder) {
            return builder
                    .given("admin has at least one device")
                    .uponReceiving("GET /devices with valid Bearer token")
                    .path("/api/v1/devices")
                    .method("GET")
                    .headers(Map.of("Authorization", "Bearer some-valid-token"))
                    .willRespondWith()
                    .status(200)
                    .headers(Map.of("Content-Type", "application/json"))
                    .body(new au.com.dius.pact.consumer.dsl.PactDslJsonArray()
                            .object()
                                .stringType("id")
                                .stringType("name")
                                .stringType("status")
                                .stringType("lifecycleStatus")
                                .stringType("organizationId")
                            .closeObject())
                    .toPact();
        }

        @Test
        @PactTestFor(pactMethod = "deviceListPact")
        @DisplayName("GET /devices → 200 array with id, name, status, lifecycleStatus, organizationId")
        void deviceList_contract(MockServer mockServer) {
            RestTemplate rest = noThrowRestTemplate();
            HttpHeaders headers = new HttpHeaders();
            headers.set("Authorization", "Bearer some-valid-token");

            ResponseEntity<String> response = rest.exchange(
                    mockServer.getUrl() + "/api/v1/devices",
                    HttpMethod.GET,
                    new HttpEntity<>(headers),
                    String.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).startsWith("[");
        }

        @Pact(consumer = "sentinel-frontend")
        RequestResponsePact deviceNotFoundPact(PactDslWithProvider builder) {
            return builder
                    .given("device does not exist")
                    .uponReceiving("GET /devices/{id} for unknown UUID")
                    .pathFromProviderState(
                            "/api/v1/devices/${deviceId}",
                            "/api/v1/devices/00000000-0000-0000-0000-000000000000")
                    .method("GET")
                    .headers(Map.of("Authorization", "Bearer some-valid-token"))
                    .willRespondWith()
                    .status(404)
                    .headers(Map.of("Content-Type", "application/json"))
                    .body(new PactDslJsonBody()
                            .stringType("type")
                            .integerType("status")
                            .stringType("detail"))
                    .toPact();
        }

        @Test
        @PactTestFor(pactMethod = "deviceNotFoundPact")
        @DisplayName("GET /devices/{unknown-id} → 404 with ProblemDetail type/status/detail")
        void deviceNotFound_contract(MockServer mockServer) {
            RestTemplate rest = noThrowRestTemplate();
            HttpHeaders headers = new HttpHeaders();
            headers.set("Authorization", "Bearer some-valid-token");

            ResponseEntity<String> response = rest.exchange(
                    mockServer.getUrl() + "/api/v1/devices/00000000-0000-0000-0000-000000000000",
                    HttpMethod.GET,
                    new HttpEntity<>(headers),
                    String.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
            assertThat(response.getBody()).contains("type");
            assertThat(response.getBody()).contains("status");
            assertThat(response.getBody()).contains("detail");
        }

        @Pact(consumer = "sentinel-frontend")
        RequestResponsePact unauthenticatedPact(PactDslWithProvider builder) {
            return builder
                    .given("no valid session")
                    .uponReceiving("GET /devices without Authorization header")
                    .path("/api/v1/devices")
                    .method("GET")
                    .willRespondWith()
                    .status(403)
                    .toPact();
        }

        @Test
        @PactTestFor(pactMethod = "unauthenticatedPact")
        @DisplayName("GET /devices without Authorization → 403 Forbidden")
        void unauthenticated_contract(MockServer mockServer) {
            RestTemplate rest = noThrowRestTemplate();

            ResponseEntity<String> response = rest.exchange(
                    mockServer.getUrl() + "/api/v1/devices",
                    HttpMethod.GET,
                    HttpEntity.EMPTY,
                    String.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        }
    }

    // ── helper ────────────────────────────────────────────────────────────────

    // RestTemplate that does not throw on 4xx/5xx — lets tests inspect the response.
    private RestTemplate noThrowRestTemplate() {
        RestTemplate rest = new RestTemplate();
        rest.setErrorHandler(new org.springframework.web.client.DefaultResponseErrorHandler() {
            @Override
            public boolean hasError(HttpStatusCode statusCode) {
                return false;
            }
        });
        return rest;
    }
}
