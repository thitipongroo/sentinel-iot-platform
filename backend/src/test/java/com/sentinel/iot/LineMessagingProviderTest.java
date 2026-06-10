package com.sentinel.iot;

import com.sentinel.iot.service.notification.LineMessagingProvider;
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

    // ---- isEnabled (parameterized) -----------------------------------------

    record IsEnabledCase(String token, String to, boolean enabled, boolean expected) {}

    static Stream<IsEnabledCase> isEnabledCases() {
        return Stream.of(
            new IsEnabledCase("",         "Uxxxxxxxx",  true,  false),  // blank token
            new IsEnabledCase("token123", "",           true,  false),  // blank to
            new IsEnabledCase("token123", "Uxxxxxxxx",  false, false),  // flag off
            new IsEnabledCase("token123", "Uxxxxxxxx",  true,  true)    // fully configured
        );
    }

    @ParameterizedTest(name = "token=\"{0}\", to=\"{1}\", enabled={2} → {3}")
    @MethodSource("isEnabledCases")
    void isEnabled_returnsExpectedResult(IsEnabledCase c) {
        configure(c.token(), c.to(), c.enabled());
        assertThat(provider.isEnabled()).isEqualTo(c.expected());
    }

    // ---- providerName -------------------------------------------------------

    @Test
    void providerName_returnsLineMessaging() {
        assertThat(provider.providerName()).isEqualTo("line-messaging");
    }

    // ---- send ---------------------------------------------------------------

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
