package com.sentinel.iot.security;

import java.util.UUID;

/**
 * Carries the authenticated user's organization ID for the duration of an HTTP request.
 * Populated by JwtAuthFilter from the JWT "orgId" claim and cleared in a finally block
 * to prevent cross-request contamination in thread-pool environments.
 *
 * Not set on MQTT-consumer or scheduler threads — callers that require a tenant context
 * (e.g. DeviceService) must guard with a null check before filtering.
 */
public final class TenantContext {

    private static final ThreadLocal<UUID> CURRENT = new ThreadLocal<>();

    private TenantContext() {}

    public static void set(UUID orgId) {
        CURRENT.set(orgId);
    }

    public static UUID get() {
        return CURRENT.get();
    }

    public static void clear() {
        CURRENT.remove();
    }
}
