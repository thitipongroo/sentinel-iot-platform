package com.sentinel.iot;

import com.sentinel.iot.service.notification.AppriseNotificationProvider;
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

class AppriseNotificationProviderTest {

    AppriseNotificationProvider provider;
    MockRestServiceServer mockServer;

    @SuppressWarnings("null")
    @BeforeEach
    void setUp() {
        provider = new AppriseNotificationProvider();
        RestTemplate restTemplate = (RestTemplate) ReflectionTestUtils.getField(provider, "restTemplate");
        mockServer = MockRestServiceServer.createServer(restTemplate);
    }

    @SuppressWarnings("null")
    private void configure(String baseUrl, String tag, boolean enabled) {
        ReflectionTestUtils.setField(provider, "baseUrl", baseUrl);
        ReflectionTestUtils.setField(provider, "tag", tag);
        ReflectionTestUtils.setField(provider, "enabled", enabled);
    }

    @Test
    void isEnabled_falseWhenBaseUrlBlank() {
        configure("", "", true);
        assertThat(provider.isEnabled()).isFalse();
    }

    @Test
    void isEnabled_falseWhenEnabledFlagFalse() {
        configure("http://apprise:8000", "", false);
        assertThat(provider.isEnabled()).isFalse();
    }

    @Test
    void isEnabled_trueWhenUrlSetAndEnabled() {
        configure("http://apprise:8000", "", true);
        assertThat(provider.isEnabled()).isTrue();
    }

    @Test
    void providerName_returnsApprise() {
        assertThat(provider.providerName()).isEqualTo("apprise");
    }

    @SuppressWarnings("null")
    @Test
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
    void send_stripsTrailingSlashFromBaseUrl() {
        configure("http://apprise:8000/", "", true);
        mockServer.expect(requestTo("http://apprise:8000/notify"))
                .andRespond(withSuccess("", MediaType.APPLICATION_JSON));

        provider.send("Test");
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
        configure("http://apprise:8000", "", true);
        mockServer.expect(requestTo("http://apprise:8000/notify"))
                .andRespond(withServerError());

        assertThatNoException().isThrownBy(() -> provider.send("Test message"));
    }
}
