package com.sentinel.iot.model;

public enum DeviceLifecycleStatus {
    /** Registered but not yet commissioned — telemetry is accepted but alerts suppressed. */
    PROVISIONED,

    /** Fully operational — telemetry accepted and alerts active. */
    ACTIVE,

    /** Temporarily disabled — telemetry is rejected until reactivated. */
    INACTIVE,

    /** Permanently retired — telemetry is rejected and the device is read-only. */
    DECOMMISSIONED
}
