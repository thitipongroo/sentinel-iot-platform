package com.sentinel.iot.exception;

/**
 * Thrown when an Optimistic Concurrency Control (OCC) version conflict occurs
 * during a Device Twin / Shadow state update.
 */
public class DeviceShadowConflictException extends RuntimeException {

    private final Long currentVersion;

    public DeviceShadowConflictException(String message, Long currentVersion) {
        super(message);
        this.currentVersion = currentVersion;
    }

    public Long getCurrentVersion() {
        return currentVersion;
    }
}
