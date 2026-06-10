package com.sentinel.iot;

import com.sentinel.iot.service.notification.WebhookNotificationProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

@Tag("unit")
@DisplayName("WebhookNotificationProvider")
class WebhookNotificationProviderTest {

    private static final String WEBHOOK_URL = "https://example.com/webhook";
    private static final String SECRET      = "test-signing-secret";

    private WebhookNotificationProvider provider;
    private MockRestServiceServer        mockServer;

    @SuppressWarnings("null")
    @BeforeEach
    void setUp() {
        provider = new WebhookNotificationProvider();
        RestTemplate restTemplate = (RestTemplate) ReflectionTestUtils.getField(provider, "restTemplate");
        mockServer = MockRestServiceServer.createServer(restTemplate);
    }

    @SuppressWarnings("null")
    private void configure(boolean enabled, String webhookUrl, String secret) {
        ReflectionTestUtils.setField(provider, "enabled",    enabled);
        ReflectionTestUtils.setField(provider, "webhookUrl", webhookUrl);
        ReflectionTestUtils.setField(provider, "secret",     secret);
    }

    // ── isEnabled ─────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("isEnabled")
    class IsEnabled {

        record Case(boolean enabled, String webhookUrl, boolean expected) {}

        static Stream<Case> cases() {
            return Stream.of(
                new Case(false, WEBHOOK_URL, false),  // flag off
                new Case(true,  null,         false),  // null URL
                new Case(true,  "",           false),  // blank URL
                new Case(true,  "   ",        false),  // whitespace-only URL
                new Case(true,  WEBHOOK_URL,  true)    // fully configured
            );
        }

        @ParameterizedTest(name = "enabled={0}, url=\"{1}\" → {2}")
        @MethodSource("cases")
        @DisplayName("isEnabled reflects the conjunction of the flag and a non-blank webhook URL")
        void isEnabled_returnsExpectedResult(Case c) {
            configure(c.enabled(), c.webhookUrl(), "");
            assertThat(provider.isEnabled())
                    .as("isEnabled(enabled=%s, url='%s')", c.enabled(), c.webhookUrl())
                    .isEqualTo(c.expected());
        }
    }

    // ── providerName ──────────────────────────────────────────────────────────

    @Nested
    @DisplayName("providerName")
    class ProviderName {

        @Test
        @DisplayName("returns the canonical identifier 'webhook'")
        void providerName_returnsWebhook() {
            assertThat(provider.providerName()).isEqualTo("webhook");
        }
    }

    // ── send — no signing secret ──────────────────────────────────────────────

    @Nested
    @DisplayName("send — without HMAC secret")
    class SendNoSecret {

        @SuppressWarnings("null")
        @Test
        @DisplayName("POSTs JSON to the webhook URL and omits signature/timestamp headers when no secret is set")
        void send_postsJsonBody_noSignatureWhenNoSecret() {
            configure(true, WEBHOOK_URL, "");
            mockServer.expect(requestTo(WEBHOOK_URL))
                    .andExpect(method(HttpMethod.POST))
                    .andExpect(header("Content-Type", MediaType.APPLICATION_JSON_VALUE))
                    .andExpect(headerDoesNotExist("X-Sentinel-Signature"))
                    .andExpect(headerDoesNotExist("X-Sentinel-Timestamp"))
                    .andRespond(withSuccess());

            provider.send("Test alert message");

            mockServer.verify();
        }

        @SuppressWarnings("null")
        @Test
        @DisplayName("body contains the message text and a unix timestamp field")
        void send_bodyContainsMessageAndTimestamp() {
            configure(true, WEBHOOK_URL, "");
            mockServer.expect(requestTo(WEBHOOK_URL))
                    .andExpect(content().string(
                            org.hamcrest.Matchers.matchesPattern(
                                    "\\{\"message\": \"Test alert\", \"timestamp\": \"\\d+\"\\}")))
                    .andRespond(withSuccess());

            provider.send("Test alert");

            mockServer.verify();
        }
    }

    // ── send — with HMAC secret ───────────────────────────────────────────────

    @Nested
    @DisplayName("send — with HMAC secret")
    class SendWithSecret {

        @SuppressWarnings("null")
        @Test
        @DisplayName("adds X-Sentinel-Signature and X-Sentinel-Timestamp headers; signature is verifiable")
        void send_addsHmacSignatureAndTimestampHeaders() {
            configure(true, WEBHOOK_URL, SECRET);
            mockServer.expect(requestTo(WEBHOOK_URL))
                    .andExpect(method(HttpMethod.POST))
                    .andExpect(request -> {
                        String sigHeader = request.getHeaders().getFirst("X-Sentinel-Signature");
                        String tsHeader  = request.getHeaders().getFirst("X-Sentinel-Timestamp");
                        assertThat(sigHeader).as("X-Sentinel-Signature").isNotNull().startsWith("sha256=");
                        assertThat(tsHeader).as("X-Sentinel-Timestamp").isNotNull().matches("\\d+");

                        // Recompute HMAC over the actual body to confirm the signature is correct
                        try {
                            String body = new String(
                                    ((org.springframework.mock.http.client.MockClientHttpRequest) request)
                                            .getBodyAsBytes(),
                                    StandardCharsets.UTF_8);
                            Mac mac = Mac.getInstance("HmacSHA256");
                            mac.init(new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
                            String expectedSig = "sha256=" + Base64.getEncoder()
                                    .encodeToString(mac.doFinal(body.getBytes(StandardCharsets.UTF_8)));
                            assertThat(sigHeader)
                                    .as("HMAC signature must match recomputed value")
                                    .isEqualTo(expectedSig);
                        } catch (Exception e) {
                            throw new AssertionError("HMAC recomputation failed: " + e.getMessage(), e);
                        }
                    })
                    .andRespond(withSuccess());

            provider.send("CRITICAL: temperature 110°C");

            mockServer.verify();
        }
    }

    // ── send — disabled / HTTP errors ─────────────────────────────────────────

    @Nested
    @DisplayName("send — disabled / error handling")
    class SendEdgeCases {

        @Test
        @DisplayName("is a no-op and makes no HTTP call when disabled")
        void send_doesNothing_whenDisabled() {
            configure(false, WEBHOOK_URL, SECRET);

            provider.send("should not be sent");

            mockServer.verify(); // no request expected
        }

        @SuppressWarnings("null")
        @Test
        @DisplayName("swallows HTTP 5xx errors and does not propagate an exception")
        void send_handlesHttpError_gracefully() {
            configure(true, WEBHOOK_URL, "");
            mockServer.expect(requestTo(WEBHOOK_URL)).andRespond(withServerError());

            assertThatNoException()
                    .as("HTTP 500 from webhook target must not propagate")
                    .isThrownBy(() -> provider.send("some alert"));
        }
    }
}
