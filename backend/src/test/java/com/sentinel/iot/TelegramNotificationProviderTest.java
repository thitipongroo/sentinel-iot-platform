package com.sentinel.iot;

import com.sentinel.iot.service.notification.TelegramNotificationProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class TelegramNotificationProviderTest {

    TelegramNotificationProvider provider;
    MockRestServiceServer mockServer;

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
        ReflectionTestUtils.setField(provider, "chatId", chatId);
        ReflectionTestUtils.setField(provider, "enabled", enabled);
    }

    // ---- isEnabled (parameterized) -----------------------------------------

    record IsEnabledCase(String botToken, String chatId, boolean enabled, boolean expected) {}

    static Stream<IsEnabledCase> isEnabledCases() {
        return Stream.of(
            new IsEnabledCase("",            "-100123456",   true,  false),  // blank token
            new IsEnabledCase("bot:token123", "",            true,  false),  // blank chatId
            new IsEnabledCase("bot:token123", "-100123456",  false, false),  // flag off
            new IsEnabledCase("bot:token123", "-100123456",  true,  true)    // fully configured
        );
    }

    @ParameterizedTest(name = "token=\"{0}\", chatId=\"{1}\", enabled={2} → {3}")
    @MethodSource("isEnabledCases")
    void isEnabled_returnsExpectedResult(IsEnabledCase c) {
        configure(c.botToken(), c.chatId(), c.enabled());
        assertThat(provider.isEnabled()).isEqualTo(c.expected());
    }

    // ---- providerName -------------------------------------------------------

    @Test
    void providerName_returnsTelegram() {
        assertThat(provider.providerName()).isEqualTo("telegram");
    }

    // ---- send ---------------------------------------------------------------

    @SuppressWarnings("null")
    @Test
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
    void send_doesNothingWhenDisabled() {
        configure("", "", false);
        provider.send("Test message");
        mockServer.verify();
    }

    @SuppressWarnings("null")
    @Test
    void send_handlesHttpErrorGracefully() {
        String token = "bot123:ABC";
        configure(token, "-1001234567890", true);
        String expectedUrl = "https://api.telegram.org/bot" + token + "/sendMessage";
        mockServer.expect(requestTo(expectedUrl))
                .andRespond(withServerError());

        assertThatNoException().isThrownBy(() -> provider.send("Test message"));
    }
}
