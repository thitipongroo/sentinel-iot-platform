package com.sentinel.iot;

import com.sentinel.iot.service.notification.LineNotifyProvider;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;

/**
 * LINE Notify was shut down on 2025-03-31.
 * LineNotifyProvider is a tombstone — permanently disabled.
 */
@Tag("unit")
@DisplayName("LineNotifyProvider (tombstone — LINE Notify shut down 2025-03-31)")
class LineNotifyProviderTest {

    private final LineNotifyProvider provider = new LineNotifyProvider();

    // ── Tombstone contract ────────────────────────────────────────────────────

    @Nested
    @DisplayName("Tombstone contract")
    class TombstoneContract {

        @Test
        @DisplayName("isEnabled always returns false because the service is permanently shut down")
        void isEnabled_alwaysReturnsFalse() {
            assertThat(provider.isEnabled())
                    .as("LINE Notify tombstone must always report disabled")
                    .isFalse();
        }

        @Test
        @DisplayName("providerName returns the tombstone identifier so logs remain distinguishable")
        void providerName_returnsTombstoneName() {
            assertThat(provider.providerName())
                    .as("tombstone provider name")
                    .isEqualTo("line-notify-tombstone");
        }

        @Test
        @DisplayName("send() does not throw so the notification pipeline is not interrupted")
        void send_doesNotThrow() {
            assertThatNoException()
                    .as("tombstone send() must be a safe no-op")
                    .isThrownBy(() -> provider.send("any message"));
        }
    }
}
