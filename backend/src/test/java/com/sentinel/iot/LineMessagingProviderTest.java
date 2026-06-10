package com.sentinel.iot;

import com.sentinel.iot.service.notification.LineMessagingProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

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
        ReflectionTestUtils.setField(provider, "to", to);
        ReflectionTestUtils.setField(provider, "enabled", enabled);
    }

    @Test
    void isEnabled_falseWhenTokenBlank() {
        configure("", "Uxxxxxxxx", true);
        assertThat(provider.isEnabled()).isFalse();
    }

    @Test
    void isEnabled_falseWhenToBlank() {
        configure("token123", "", true);
        assertThat(provider.isEnabled()).isFalse();
    }

    @Test
    void isEnabled_falseWhenEnabledFlagFalse() {
        configure("token123", "Uxxxxxxxx", false);
        assertThat(provider.isEnabled()).isFalse();
    }

    @Test
    void isEnabled_trueWhenFullyConfigured() {
        configure("token123", "Uxxxxxxxx", true);
        assertThat(provider.isEnabled()).isTrue();
    }

    @Test
    void providerName_returnsLineMessaging() {
        assertThat(provider.providerName()).isEqualTo("line-messaging");
    }

    @SuppressWarnings("null")
    @Test
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
    void send_doesNothingWhenDisabled() {
        configure("", "", false);
        // isEnabled() returns false → send() returns immediately without HTTP call
        provider.send("Test message");
        mockServer.verify();
    }

    @Test
    void send_handlesHttpErrorGracefully() {
        configure("channel-token-abc", "U12345678", true);
        mockServer.expect(requestTo(PUSH_URL))
                .andRespond(withServerError());

        assertThatNoException().isThrownBy(() -> provider.send("Test message"));
    }
}
