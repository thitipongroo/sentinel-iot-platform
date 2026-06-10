package com.sentinel.iot;

import com.sentinel.iot.service.notification.LineMessagingProvider;
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
@DisplayName("LineMessagingProvider")
class LineMessagingProviderTest {

    private static final String PUSH_URL = "https://api.line.me/v2/bot/message/push";

    LineMessagingProvider provider;
    MockRestServiceServer mockServer;

    @SuppressWarnings("null")
    @BeforeEach
    void setUp() {
        provider = new LineMessagingProvider();
        RestTemplate restTemplate = (RestTemplate) ReflectionTestUtils.getField(provider, "restTemplate");
        mockServer = MockRestServiceServer.createServer(restTemplate);
    }

    @SuppressWarnings("null")
    private void configure(String token, String to, boolean enabled) {
        ReflectionTestUtils.setField(provider, "channelToken", token);
        ReflectionTestUtils.setField(provider, "to",           to);
        ReflectionTestUtils.setField(provider, "enabled",      enabled);
    }

    // ── isEnabled ─────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("isEnabled")
    class IsEnabled {

        record Case(String token, String to, boolean enabled, boolean expected) {}

        static Stream<Case> cases() {
            return Stream.of(
                new Case("",         "Uxxxxxxxx", true,  false),  // blank channel token
                new Case("token123", "",          true,  false),  // blank recipient
                new Case("token123", "Uxxxxxxxx", false, false),  // flag off
                new Case("token123", "Uxxxxxxxx", true,  true)    // fully configured
            );
        }

        @ParameterizedTest(name = "token=\"{0}\", to=\"{1}\", enabled={2} → {3}")
        @MethodSource("cases")
        @DisplayName("isEnabled requires a non-blank token, non-blank recipient, and the enabled flag")
        void isEnabled_returnsExpectedResult(Case c) {
            configure(c.token(), c.to(), c.enabled());
            assertThat(provider.isEnabled())
                    .as("isEnabled(token='%s', to='%s', enabled=%s)", c.token(), c.to(), c.enabled())
                    .isEqualTo(c.expected());
        }
    }

    // ── providerName ──────────────────────────────────────────────────────────

    @Nested
    @DisplayName("providerName")
    class ProviderName {

        @Test
        @DisplayName("returns the canonical identifier 'line-messaging'")
        void providerName_returnsLineMessaging() {
            assertThat(provider.providerName()).isEqualTo("line-messaging");
        }
    }

    // ── send ──────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("send")
    class Send {

        @SuppressWarnings("null")
        @Test
        @DisplayName("POSTs to the LINE push endpoint with a Bearer Authorization header")
        void send_postsToLineApiWithCorrectHeaders() {
            configure("channel-token-abc", "U12345678", true);
            mockServer.expect(requestTo(PUSH_URL))
                    .andExpect(method(HttpMethod.POST))
                    .andExpect(header("Authorization", "Bearer channel-token-abc"))
                    .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                    .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

            provider.send("Temperature exceeded threshold");

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
            configure("channel-token-abc", "U12345678", true);
            mockServer.expect(requestTo(PUSH_URL)).andRespond(withServerError());

            assertThatNoException()
                    .as("HTTP 500 from LINE must not propagate")
                    .isThrownBy(() -> provider.send("Test message"));
        }
    }
}
