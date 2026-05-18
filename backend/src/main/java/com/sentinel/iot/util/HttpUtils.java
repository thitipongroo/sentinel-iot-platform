package com.sentinel.iot.util;

import jakarta.servlet.http.HttpServletRequest;

public final class HttpUtils {

    private HttpUtils() {}

    /**
     * Resolves the real client IP from a request, honoring X-Forwarded-For when present
     * (set by load balancers / reverse proxies in production).
     */
    public static String resolveClientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        return forwarded != null ? forwarded.split(",")[0].trim() : request.getRemoteAddr();
    }
}
