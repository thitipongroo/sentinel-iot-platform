package com.sentinel.iot;

import com.sentinel.iot.service.notification.LineNotifyProvider;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;

/**
 * LINE Notify was shut down on 2025-03-31.
 * LineNotifyProvider is a tombstone — permanently disabled.
 */
class LineNotifyProviderTest {

    private final LineNotifyProvider provider = new LineNotifyProvider();

    @Test
    void isEnabled_alwaysReturnsFalse() {
        assertThat(provider.isEnabled()).isFalse();
    }

    @Test
    void providerName_returnsTombstoneName() {
        assertThat(provider.providerName()).isEqualTo("line-notify-tombstone");
    }

    @Test
    void send_doesNotThrow_andLogsError() {
        assertThatNoException().isThrownBy(() -> provider.send("any message"));
    }
}
