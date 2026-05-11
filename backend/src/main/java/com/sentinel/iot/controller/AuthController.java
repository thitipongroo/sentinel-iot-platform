package com.sentinel.iot.controller;

import com.sentinel.iot.dto.AuthRequest;
import com.sentinel.iot.dto.AuthResponse;
import com.sentinel.iot.dto.RefreshRequest;
import com.sentinel.iot.model.AppUser;
import com.sentinel.iot.model.RefreshToken;
import com.sentinel.iot.repository.AppUserRepository;
import com.sentinel.iot.service.AuditService;
import com.sentinel.iot.service.JwtService;
import com.sentinel.iot.service.UserDetailsServiceImpl;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@Tag(name = "Authentication", description = "Login, token refresh, and logout")
public class AuthController {

    private final AuthenticationManager authManager;
    private final JwtService jwtService;
    private final UserDetailsServiceImpl userDetailsService;
    private final AuditService auditService;
    private final AppUserRepository appUserRepository;

    @PostMapping("/login")
    @Operation(summary = "Authenticate and receive access + refresh tokens")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody AuthRequest req,
                                              HttpServletRequest httpRequest) {
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

        auditService.log(req.getUsername(), "LOGIN", "/api/v1/auth/login", null, getClientIp(httpRequest));

        return ResponseEntity.ok(new AuthResponse(accessToken, refreshToken.getToken(), role, req.getUsername()));
    }

    @PostMapping("/refresh")
    @Operation(summary = "Rotate refresh token and receive a new access token")
    public ResponseEntity<AuthResponse> refresh(@Valid @RequestBody RefreshRequest req) {
        RefreshToken newRefreshToken = jwtService.rotateRefreshToken(req.getRefreshToken());
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
        return ResponseEntity.ok(new AuthResponse(accessToken, newRefreshToken.getToken(), role, username));
    }

    @PostMapping("/logout")
    @Operation(summary = "Revoke the current access token and all refresh tokens")
    public ResponseEntity<Void> logout(Authentication authentication,
                                       HttpServletRequest httpRequest) {
        if (authentication != null) {
            // Revoke all refresh tokens (prevents silent re-authentication)
            jwtService.revokeAllRefreshTokens(authentication.getName());

            // Revoke the current access token via the Redis JTI blocklist.
            // This closes the 15-minute window where a token remains valid after logout.
            String authHeader = httpRequest.getHeader("Authorization");
            if (authHeader != null && authHeader.startsWith("Bearer ")) {
                jwtService.revokeAccessToken(authHeader.substring(7));
            }

            auditService.log(authentication.getName(), "LOGOUT", "/api/v1/auth/logout", null,
                    getClientIp(httpRequest));
        }
        return ResponseEntity.noContent().build();
    }

    private String getClientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        return (forwarded != null) ? forwarded.split(",")[0].trim() : request.getRemoteAddr();
    }
}
