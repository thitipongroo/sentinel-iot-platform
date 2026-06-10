package com.sentinel.iot;

import com.sentinel.iot.service.notification.AppriseNotificationProvider;
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
@DisplayName("AppriseNotificationProvider")
class AppriseNotificationProviderTest {

    AppriseNotificationProvider provider;
    MockRestServiceServer        mockServer;

    @SuppressWarnings("null")
    @BeforeEach
    void setUp() {
        provider = new AppriseNotificationProvider();
        RestTemplate restTemplate = (RestTemplate) ReflectionTestUtils.getField(provider, "restTemplate");
        mockServer = MockRestServiceServer.createServer(restTemplate);
    }

    @SuppressWarnings("null")
    private void configure(String baseUrl, String tag, boolean enabled) {
        ReflectionTestUtils.setField(provider, "baseUrl",  baseUrl);
        ReflectionTestUtils.setField(provider, "tag",      tag);
        ReflectionTestUtils.setField(provider, "enabled",  enabled);
    }

    // ── isEnabled ─────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("isEnabled")
    class IsEnabled {

        record Case(String baseUrl, boolean enabled, boolean expected) {}

        static Stream<Case> cases() {
            return Stream.of(
                new Case("",                    true,  false),  // blank base URL
                new Case("http://apprise:8000", false, false),  // flag off
                new Case("http://apprise:8000", true,  true)    // fully configured
            );
        }

        @ParameterizedTest(name = "url=\"{0}\", enabled={1} → {2}")
        @MethodSource("cases")
        @DisplayName("isEnabled requires a non-blank base URL and the enabled flag")
        void isEnabled_returnsExpectedResult(Case c) {
            configure(c.baseUrl(), "", c.enabled());
            assertThat(provider.isEnabled())
                    .as("isEnabled(url='%s', enabled=%s)", c.baseUrl(), c.enabled())
                    .isEqualTo(c.expected());
        }
    }

    // ── providerName ──────────────────────────────────────────────────────────

    @Nested
    @DisplayName("providerName")
    class ProviderName {

        @Test
        @DisplayName("returns the canonical identifier 'apprise'")
        void providerName_returnsApprise() {
            assertThat(provider.providerName()).isEqualTo("apprise");
        }
    }

    // ── send ──────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("send")
    class Send {

        @SuppressWarnings("null")
        @Test
        @DisplayName("POSTs to /notify when no tag is configured")
        void send_postsToNotifyEndpointWhenNoTag() {
            configure("http://apprise:8000", "", true);
            mockServer.expect(requestTo("http://apprise:8000/notify"))
                    .andExpect(method(HttpMethod.POST))
                    .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                    .andRespond(withSuccess("", MediaType.APPLICATION_JSON));

            provider.send("Temperature alert");

            mockServer.verify();
        }

        @SuppressWarnings("null")
        @Test
        @DisplayName("POSTs to /notify/{tag} when a tag is configured")
        void send_postsToTagEndpointWhenTagSet() {
            configure("http://apprise:8000", "iot-alerts", true);
            mockServer.expect(requestTo("http://apprise:8000/notify/iot-alerts"))
                    .andExpect(method(HttpMethod.POST))
                    .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                    .andRespond(withSuccess("", MediaType.APPLICATION_JSON));

            provider.send("Temperature alert");

            mockServer.verify();
        }

        @SuppressWarnings("null")
        @Test
        @DisplayName("strips a trailing slash from the base URL before appending /notify")
        void send_stripsTrailingSlashFromBaseUrl() {
            configure("http://apprise:8000/", "", true);
            mockServer.expect(requestTo("http://apprise:8000/notify"))
                    .andRespond(withSuccess("", MediaType.APPLICATION_JSON));

            provider.send("Test");

            mockServer.verify();
        }

        @Test
        @DisplayName("is a no-op and makes no HTTP call when disabled")
        void send_doesNothingWhenDisabled() {
            configure("", "", false);

            provider.send("Test message");

            mockServer.verify(); // no request expected
        }

        @SuppressWarnings("null")
        @Test
        @DisplayName("swallows HTTP 5xx errors and does not propagate an exception")
        void send_handlesHttpErrorGracefully() {
            configure("http://apprise:8000", "", true);
            mockServer.expect(requestTo("http://apprise:8000/notify"))
                    .andRespond(withServerError());

            assertThatNoException()
                    .as("HTTP 500 from Apprise must not propagate")
                    .isThrownBy(() -> provider.send("Test message"));
        }
    }
}
