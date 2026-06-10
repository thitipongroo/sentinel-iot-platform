package com.sentinel.iot;

import com.sentinel.iot.service.notification.SlackNotificationProvider;
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

import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

@Tag("unit")
@DisplayName("SlackNotificationProvider")
class SlackNotificationProviderTest {

    private static final String WEBHOOK_URL = "https://hooks.slack.com/services/T00/B00/test";

    private SlackNotificationProvider provider;
    private MockRestServiceServer      mockServer;

    @SuppressWarnings("null")
    @BeforeEach
    void setUp() {
        provider = new SlackNotificationProvider();
        RestTemplate restTemplate = (RestTemplate) ReflectionTestUtils.getField(provider, "restTemplate");
        mockServer = MockRestServiceServer.createServer(restTemplate);
    }

    @SuppressWarnings("null")
    private void configure(boolean enabled, String webhookUrl) {
        ReflectionTestUtils.setField(provider, "enabled",    enabled);
        ReflectionTestUtils.setField(provider, "webhookUrl", webhookUrl);
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
            configure(c.enabled(), c.webhookUrl());
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
        @DisplayName("returns the canonical identifier 'slack'")
        void providerName_returnsSlack() {
            assertThat(provider.providerName()).isEqualTo("slack");
        }
    }

    // ── send ──────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("send")
    class Send {

        @SuppressWarnings("null")
        @Test
        @DisplayName("POSTs a JSON body to the configured webhook URL")
        void send_postsJsonBodyToWebhookUrl() {
            configure(true, WEBHOOK_URL);
            mockServer.expect(requestTo(WEBHOOK_URL))
                    .andExpect(method(HttpMethod.POST))
                    .andExpect(header("Content-Type", MediaType.APPLICATION_JSON_VALUE))
                    .andExpect(content().string("{\"text\": \"Alert: sensor-1 exceeded threshold\"}"))
                    .andRespond(withSuccess());

            provider.send("Alert: sensor-1 exceeded threshold");

            mockServer.verify();
        }

        @SuppressWarnings("null")
        @Test
        @DisplayName("escapes double-quote characters inside the message text")
        void send_escapesDoubleQuotesInMessage() {
            configure(true, WEBHOOK_URL);
            mockServer.expect(requestTo(WEBHOOK_URL))
                    .andExpect(content().string("{\"text\": \"alert with \\\"quotes\\\"\"}"))
                    .andRespond(withSuccess());

            provider.send("alert with \"quotes\"");

            mockServer.verify();
        }

        @Test
        @DisplayName("is a no-op and makes no HTTP call when disabled")
        void send_doesNothing_whenDisabled() {
            configure(false, WEBHOOK_URL);

            provider.send("should not be sent");

            mockServer.verify(); // no request expected
        }

        @SuppressWarnings("null")
        @Test
        @DisplayName("swallows HTTP 5xx errors and does not propagate an exception")
        void send_handlesHttpError_gracefully() {
            configure(true, WEBHOOK_URL);
            mockServer.expect(requestTo(WEBHOOK_URL))
                    .andRespond(withServerError());

            assertThatNoException()
                    .as("HTTP 500 from Slack must not propagate")
                    .isThrownBy(() -> provider.send("some alert"));
        }
    }
}
