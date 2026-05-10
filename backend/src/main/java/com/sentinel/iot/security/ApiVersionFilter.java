package com.sentinel.iot.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Stamps every API response with the current API version and,
 * for unversioned /api/* paths, adds a Deprecation header pointing
 * callers to the /api/v1/* equivalent.
 *
 * Header contract:
 *   API-Version: 1                       — always present on /api/** responses
 *   Deprecation: true                    — present when caller hit an unversioned path
 *   Link: </api/v1/...>; rel="successor-version"  — redirect hint for unversioned callers
 *   Sunset: Sat, 01 Jan 2027 00:00:00 GMT          — when unversioned routes will be removed
 */
@Component
@Order(1)
public class ApiVersionFilter extends OncePerRequestFilter {

    private static final String CURRENT_VERSION = "1";
    private static final String SUNSET_DATE     = "Sat, 01 Jan 2027 00:00:00 GMT";

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return !path.startsWith("/api/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        response.setHeader("API-Version", CURRENT_VERSION);

        String path = request.getRequestURI();
        if (isUnversioned(path)) {
            String versioned = path.replaceFirst("^/api/", "/api/v1/");
            response.setHeader("Deprecation",  "true");
            response.setHeader("Sunset",        SUNSET_DATE);
            response.setHeader("Link",          "<" + versioned + ">; rel=\"successor-version\"");
        }

        chain.doFilter(request, response);
    }

    private boolean isUnversioned(String path) {
        // Paths that are NOT under /api/v{digit}/ are considered unversioned
        return path.startsWith("/api/") && !path.matches("^/api/v\\d+/.*");
    }
}
