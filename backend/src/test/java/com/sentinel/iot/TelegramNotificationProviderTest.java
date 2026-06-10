package com.sentinel.iot;

import com.sentinel.iot.service.notification.TelegramNotificationProvider;
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
@DisplayName("TelegramNotificationProvider")
class TelegramNotificationProviderTest {

    TelegramNotificationProvider provider;
    MockRestServiceServer         mockServer;

    @SuppressWarnings("null")
    @BeforeEach
    void setUp() {
        provider = new TelegramNotificationProvider();
        RestTemplate restTemplate = (RestTemplate) ReflectionTestUtils.getField(provider, "restTemplate");
        mockServer = MockRestServiceServer.createServer(restTemplate);
    }

    @SuppressWarnings("null")
    private void configure(String botToken, String chatId, boolean enabled) {
        ReflectionTestUtils.setField(provider, "botToken", botToken);
        ReflectionTestUtils.setField(provider, "chatId",   chatId);
        ReflectionTestUtils.setField(provider, "enabled",  enabled);
    }

    // ── isEnabled ─────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("isEnabled")
    class IsEnabled {

        record Case(String botToken, String chatId, boolean enabled, boolean expected) {}

        static Stream<Case> cases() {
            return Stream.of(
                new Case("",             "-100123456",  true,  false),  // blank bot token
                new Case("bot:token123", "",            true,  false),  // blank chat ID
                new Case("bot:token123", "-100123456",  false, false),  // flag off
                new Case("bot:token123", "-100123456",  true,  true)    // fully configured
            );
        }

        @ParameterizedTest(name = "token=\"{0}\", chatId=\"{1}\", enabled={2} → {3}")
        @MethodSource("cases")
        @DisplayName("isEnabled requires a non-blank bot token, non-blank chat ID, and the enabled flag")
        void isEnabled_returnsExpectedResult(Case c) {
            configure(c.botToken(), c.chatId(), c.enabled());
            assertThat(provider.isEnabled())
                    .as("isEnabled(token='%s', chatId='%s', enabled=%s)", c.botToken(), c.chatId(), c.enabled())
                    .isEqualTo(c.expected());
        }
    }

    // ── providerName ──────────────────────────────────────────────────────────

    @Nested
    @DisplayName("providerName")
    class ProviderName {

        @Test
        @DisplayName("returns the canonical identifier 'telegram'")
        void providerName_returnsTelegram() {
            assertThat(provider.providerName()).isEqualTo("telegram");
        }
    }

    // ── send ──────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("send")
    class Send {

        @SuppressWarnings("null")
        @Test
        @DisplayName("POSTs to the Telegram sendMessage endpoint constructed from the bot token")
        void send_postsToTelegramApiWithBotToken() {
            String token = "bot123:ABC";
            configure(token, "-1001234567890", true);
            String expectedUrl = "https://api.telegram.org/bot" + token + "/sendMessage";
            mockServer.expect(requestTo(expectedUrl))
                    .andExpect(method(HttpMethod.POST))
                    .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                    .andRespond(withSuccess("{\"ok\":true}", MediaType.APPLICATION_JSON));

            provider.send("Smoke level exceeded threshold");

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
            String token = "bot123:ABC";
            configure(token, "-1001234567890", true);
            String expectedUrl = "https://api.telegram.org/bot" + token + "/sendMessage";
            mockServer.expect(requestTo(expectedUrl)).andRespond(withServerError());

            assertThatNoException()
                    .as("HTTP 500 from Telegram must not propagate")
                    .isThrownBy(() -> provider.send("Test message"));
        }
    }
}
