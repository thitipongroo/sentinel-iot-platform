package com.sentinel.iot;

import com.sentinel.iot.service.notification.WebhookNotificationProvider;
import org.junit.jupiter.api.BeforeEach;
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

class WebhookNotificationProviderTest {

    private static final String WEBHOOK_URL = "https://example.com/webhook";
    private static final String SECRET      = "test-signing-secret";

    private WebhookNotificationProvider provider;
    private MockRestServiceServer mockServer;

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

    // ---- isEnabled (parameterized) -----------------------------------------

    record IsEnabledCase(boolean enabled, String webhookUrl, boolean expected) {}

    static Stream<IsEnabledCase> isEnabledCases() {
        return Stream.of(
            new IsEnabledCase(false, WEBHOOK_URL, false),
            new IsEnabledCase(true,  null,         false),
            new IsEnabledCase(true,  "",           false),
            new IsEnabledCase(true,  "   ",        false),
            new IsEnabledCase(true,  WEBHOOK_URL,  true)
        );
    }

    @ParameterizedTest(name = "enabled={0}, url=\"{1}\" → {2}")
    @MethodSource("isEnabledCases")
    void isEnabled_returnsExpectedResult(IsEnabledCase c) {
        configure(c.enabled(), c.webhookUrl(), "");
        assertThat(provider.isEnabled()).isEqualTo(c.expected());
    }

    // ---- providerName -------------------------------------------------------

    @Test
    void providerName_returnsWebhook() {
        assertThat(provider.providerName()).isEqualTo("webhook");
    }

    // ---- send — no secret --------------------------------------------------

    @SuppressWarnings("null")
    @Test
    void send_postsJsonBodyToWebhookUrl_noSignatureWhenNoSecret() {
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

    // ---- send — with HMAC secret -------------------------------------------

    @SuppressWarnings("null")
    @Test
    void send_addsHmacSignatureAndTimestampHeaders_whenSecretConfigured() {
        configure(true, WEBHOOK_URL, SECRET);

        // Capture the body to verify the HMAC independently
        mockServer.expect(requestTo(WEBHOOK_URL))
                .andExpect(method(HttpMethod.POST))
                .andExpect(request -> {
                    String sigHeader = request.getHeaders().getFirst("X-Sentinel-Signature");
                    String tsHeader  = request.getHeaders().getFirst("X-Sentinel-Timestamp");
                    assertThat(sigHeader).isNotNull().startsWith("sha256=");
                    assertThat(tsHeader).isNotNull().matches("\\d+");

                    // Recompute HMAC over the actual body to confirm signature is correct
                    try {
                        String body = new String(
                                ((org.springframework.mock.http.client.MockClientHttpRequest) request)
                                        .getBodyAsBytes(),
                                StandardCharsets.UTF_8);
                        Mac mac = Mac.getInstance("HmacSHA256");
                        mac.init(new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
                        String expectedSig = "sha256=" + Base64.getEncoder()
                                .encodeToString(mac.doFinal(body.getBytes(StandardCharsets.UTF_8)));
                        assertThat(sigHeader).isEqualTo(expectedSig);
                    } catch (Exception e) {
                        throw new AssertionError("HMAC recomputation failed: " + e.getMessage(), e);
                    }
                })
                .andRespond(withSuccess());

        provider.send("CRITICAL: temperature 110°C");

        mockServer.verify();
    }

    @SuppressWarnings("null")
    @Test
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

    // ---- send — disabled / error -------------------------------------------

    @Test
    void send_doesNothing_whenDisabled() {
        configure(false, WEBHOOK_URL, SECRET);

        provider.send("should not be sent");

        mockServer.verify(); // no request expected
    }

    @SuppressWarnings("null")
    @Test
    void send_handlesHttpError_gracefully() {
        configure(true, WEBHOOK_URL, "");
        mockServer.expect(requestTo(WEBHOOK_URL))
                .andRespond(withServerError());

        assertThatNoException().isThrownBy(() -> provider.send("some alert"));
    }
}
