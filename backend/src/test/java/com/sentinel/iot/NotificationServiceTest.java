package com.sentinel.iot;

import com.sentinel.iot.service.NotificationService;
import com.sentinel.iot.service.notification.NotificationProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@Tag("unit")
@DisplayName("NotificationService")
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

    // ── Routing ───────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Provider routing")
    class ProviderRouting {

        @Test
        @DisplayName("delivers message to all enabled providers")
        void send_callsAllEnabledProviders() {
            notificationService.send("Temperature exceeded threshold");

            verify(enabledProvider1).send("Temperature exceeded threshold");
            verify(enabledProvider2).send("Temperature exceeded threshold");
        }

        @Test
        @DisplayName("skips disabled providers")
        void send_skipsDisabledProviders() {
            notificationService.send("Test alert");

            verify(disabledProvider, never()).send(anyString());
        }

        @Test
        @DisplayName("does not throw when no providers are enabled")
        void send_doesNotThrow_whenNoProvidersEnabled() {
            NotificationService allDisabled = new NotificationService(List.of(disabledProvider));

            assertThatNoException().isThrownBy(() -> allDisabled.send("message"));
        }

        @Test
        @DisplayName("does not throw when the provider list is empty")
        void send_doesNotThrow_withEmptyProviderList() {
            NotificationService empty = new NotificationService(List.of());

            assertThatNoException().isThrownBy(() -> empty.send("message"));
        }
    }
}
