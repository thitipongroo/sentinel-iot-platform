package com.sentinel.iot;

import com.sentinel.iot.service.notification.SlackNotificationProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.api.Test;
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

class SlackNotificationProviderTest {

    private static final String WEBHOOK_URL = "https://hooks.slack.com/services/T00/B00/test";

    private SlackNotificationProvider provider;
    private MockRestServiceServer mockServer;

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

    // ---- isEnabled (parameterized) -----------------------------------------

    record IsEnabledCase(boolean enabled, String webhookUrl, boolean expected) {}

    static Stream<IsEnabledCase> isEnabledCases() {
        return Stream.of(
            new IsEnabledCase(false, WEBHOOK_URL,  false),
            new IsEnabledCase(true,  null,          false),
            new IsEnabledCase(true,  "",            false),
            new IsEnabledCase(true,  "   ",         false),
            new IsEnabledCase(true,  WEBHOOK_URL,   true)
        );
    }

    @ParameterizedTest(name = "enabled={0}, url=\"{1}\" → {2}")
    @MethodSource("isEnabledCases")
    void isEnabled_returnsExpectedResult(IsEnabledCase c) {
        configure(c.enabled(), c.webhookUrl());
        assertThat(provider.isEnabled()).isEqualTo(c.expected());
    }

    // ---- providerName -------------------------------------------------------

    @Test
    void providerName_returnsSlack() {
        assertThat(provider.providerName()).isEqualTo("slack");
    }

    // ---- send ---------------------------------------------------------------

    @SuppressWarnings("null")
    @Test
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

    @Test
    void send_doesNothing_whenDisabled() {
        configure(false, WEBHOOK_URL);

        provider.send("should not be sent");

        mockServer.verify(); // no request expected
    }

    @SuppressWarnings("null")
    @Test
    void send_escapesDoubleQuotesInMessage() {
        configure(true, WEBHOOK_URL);
        mockServer.expect(requestTo(WEBHOOK_URL))
                .andExpect(content().string("{\"text\": \"alert with \\\"quotes\\\"\"}"))
                .andRespond(withSuccess());

        provider.send("alert with \"quotes\"");

        mockServer.verify();
    }

    @SuppressWarnings("null")
    @Test
    void send_handlesHttpError_gracefully() {
        configure(true, WEBHOOK_URL);
        mockServer.expect(requestTo(WEBHOOK_URL))
                .andRespond(withServerError());

        assertThatNoException().isThrownBy(() -> provider.send("some alert"));
    }
}
