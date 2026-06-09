package com.sentinel.iot.controller;

import com.sentinel.iot.dto.AuthRequest;
import com.sentinel.iot.dto.AuthResponse;
import com.sentinel.iot.model.AppUser;
import com.sentinel.iot.model.RefreshToken;
import com.sentinel.iot.repository.AppUserRepository;
import com.sentinel.iot.service.AuditService;
import com.sentinel.iot.service.JwtService;
import com.sentinel.iot.service.UserDetailsServiceImpl;
import com.sentinel.iot.util.HttpUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;
import java.util.Objects;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@Tag(name = "Authentication", description = "Login, token refresh, and logout")
public class AuthController {

    private static final String REFRESH_COOKIE_NAME = "sentinel_refresh_token";
    // 7 days — matches jwt.refresh-expiration-ms in application.yml
    private static final Duration REFRESH_COOKIE_MAX_AGE = Duration.ofDays(7);

    private final AuthenticationManager authManager;
    private final JwtService jwtService;
    private final UserDetailsServiceImpl userDetailsService;
    private final AuditService auditService;
    private final AppUserRepository appUserRepository;

    @PostMapping("/login")
    @Operation(summary = "Authenticate and receive access token; refresh token set as HttpOnly cookie")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody AuthRequest req,
                                              HttpServletRequest httpRequest,
                                              HttpServletResponse httpResponse) {
        Authentication auth = authManager.authenticate(
                new UsernamePasswordAuthenticationToken(req.getUsername(), req.getPassword()));

        String role = auth.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .findFirst()
                .orElse("ROLE_OPERATOR")
                .replace("ROLE_", "");

        AppUser appUser = appUserRepository.findByUsername(req.getUsername())
                .orElseThrow(() -> new IllegalStateException("User not found after successful authentication"));
        String accessToken = jwtService.generateAccessToken(req.getUsername(), role, appUser.getOrganizationId());
        RefreshToken refreshToken = jwtService.generateRefreshToken(req.getUsername());

        setRefreshCookie(httpResponse, refreshToken.getRawToken());
        auditService.log(req.getUsername(), "LOGIN", "/api/v1/auth/login", null, HttpUtils.resolveClientIp(httpRequest));

        // refreshToken field in body is empty — the token is in the HttpOnly cookie
        return ResponseEntity.ok(new AuthResponse(accessToken, null, role, req.getUsername()));
    }

    @PostMapping("/refresh")
    @Operation(summary = "Rotate refresh token using the HttpOnly cookie; returns new access token")
    public ResponseEntity<AuthResponse> refresh(
            @CookieValue(name = REFRESH_COOKIE_NAME, required = false) String cookieToken,
            HttpServletResponse httpResponse) {

        if (cookieToken == null || cookieToken.isBlank()) {
            return ResponseEntity.status(401).build();
        }

        RefreshToken newRefreshToken = jwtService.rotateRefreshToken(cookieToken);
        String username = newRefreshToken.getUsername();

        UserDetails userDetails = userDetailsService.loadUserByUsername(username);
        String role = userDetails.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .findFirst()
                .orElse("ROLE_OPERATOR")
                .replace("ROLE_", "");

        AppUser appUser = appUserRepository.findByUsername(username)
                .orElseThrow(() -> new IllegalStateException("User not found"));
        String accessToken = jwtService.generateAccessToken(username, role, appUser.getOrganizationId());

        setRefreshCookie(httpResponse, newRefreshToken.getRawToken());
        return ResponseEntity.ok(new AuthResponse(accessToken, null, role, username));
    }

    @PostMapping("/logout")
    @Operation(summary = "Revoke the current access token and all refresh tokens; clears the cookie")
    public ResponseEntity<Void> logout(Authentication authentication,
                                       HttpServletRequest httpRequest,
                                       HttpServletResponse httpResponse) {
        if (authentication != null) {
            jwtService.revokeAllRefreshTokens(authentication.getName());

            String authHeader = httpRequest.getHeader("Authorization");
            if (authHeader != null && authHeader.startsWith("Bearer ")) {
                jwtService.revokeAccessToken(authHeader.substring(7));
            }

            auditService.log(authentication.getName(), "LOGOUT", "/api/v1/auth/logout", null,
                    HttpUtils.resolveClientIp(httpRequest));
        }
        clearRefreshCookie(httpResponse);
        return ResponseEntity.noContent().build();
    }

    private void setRefreshCookie(HttpServletResponse response, String rawToken) {
        writeRefreshCookie(response, rawToken, REFRESH_COOKIE_MAX_AGE);
    }

    private void clearRefreshCookie(HttpServletResponse response) {
        writeRefreshCookie(response, "", Duration.ZERO);
    }

    private void writeRefreshCookie(HttpServletResponse response, String token, Duration maxAge) {
        ResponseCookie cookie = ResponseCookie.from(Objects.requireNonNull(REFRESH_COOKIE_NAME), Objects.requireNonNull(token))
                .httpOnly(true)
                .secure(true)
                .sameSite("Strict")
                .path("/api/v1/auth")
                .maxAge(Objects.requireNonNull(maxAge))
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }
}
