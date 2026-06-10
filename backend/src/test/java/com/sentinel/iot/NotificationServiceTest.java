package com.sentinel.iot;

import com.sentinel.iot.service.NotificationService;
import com.sentinel.iot.service.notification.NotificationProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    @Mock NotificationProvider enabledProvider1;
    @Mock NotificationProvider enabledProvider2;
    @Mock NotificationProvider disabledProvider;

    NotificationService notificationService;

    @BeforeEach
    void setUp() {
        when(enabledProvider1.isEnabled()).thenReturn(true);
        when(enabledProvider2.isEnabled()).thenReturn(true);
        when(disabledProvider.isEnabled()).thenReturn(false);
        notificationService = new NotificationService(
                List.of(enabledProvider1, enabledProvider2, disabledProvider));
    }

    @Test
    void send_callsAllEnabledProviders() {
        notificationService.send("Temperature exceeded threshold");
        verify(enabledProvider1).send("Temperature exceeded threshold");
        verify(enabledProvider2).send("Temperature exceeded threshold");
    }

    @Test
    void send_skipsDisabledProviders() {
        notificationService.send("Test alert");
        verify(disabledProvider, never()).send(anyString());
    }

    @Test
    void send_doesNotThrowWhenNoProvidersEnabled() {
        NotificationService allDisabled = new NotificationService(List.of(disabledProvider));
        assertThatNoException().isThrownBy(() -> allDisabled.send("message"));
    }

    @Test
    void send_doesNotThrowWithEmptyProviderList() {
        NotificationService empty = new NotificationService(List.of());
        assertThatNoException().isThrownBy(() -> empty.send("message"));
    }
}
