package com.sentinel.iot.controller;

import com.sentinel.iot.dto.ChangeRoleRequest;
import com.sentinel.iot.dto.CreateUserRequest;
import com.sentinel.iot.dto.ResetPasswordRequest;
import com.sentinel.iot.dto.UserResponse;
import com.sentinel.iot.security.TenantContext;
import com.sentinel.iot.service.AuditService;
import com.sentinel.iot.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequestMapping("/api/v1/users")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
@Tag(name = "Users", description = "User management — ADMIN only")
public class UserController {

    private final UserService  userService;
    private final AuditService auditService;

    @GetMapping
    @Operation(summary = "List all users in the current organization")
    public ResponseEntity<List<UserResponse>> findAll() {
        return ResponseEntity.ok(userService.findAll(TenantContext.get()));
    }

    @PostMapping
    @Operation(summary = "Create a new user (ADMIN or OPERATOR role)")
    public ResponseEntity<UserResponse> create(
            @Valid @RequestBody CreateUserRequest req,
            Authentication auth,
            HttpServletRequest httpRequest) {
        UserResponse created = userService.create(req, TenantContext.get());
        auditService.log(auth.getName(), "USER_CREATE", "/api/v1/users",
                "username=" + req.username() + " role=" + req.role(), resolveIp(httpRequest));
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @DeleteMapping("/{username}")
    @Operation(summary = "Delete a user (cannot delete your own account)")
    public ResponseEntity<Void> delete(
            @PathVariable String username,
            Authentication auth,
            HttpServletRequest httpRequest) {
        if (auth.getName().equals(username)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cannot delete your own account");
        }
        userService.delete(username, TenantContext.get());
        auditService.log(auth.getName(), "USER_DELETE", "/api/v1/users/" + username,
                null, resolveIp(httpRequest));
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{username}/role")
    @Operation(summary = "Change a user's role (cannot change your own role)")
    public ResponseEntity<UserResponse> changeRole(
            @PathVariable String username,
            @Valid @RequestBody ChangeRoleRequest req,
            Authentication auth,
            HttpServletRequest httpRequest) {
        if (auth.getName().equals(username)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cannot change your own role");
        }
        UserResponse updated = userService.changeRole(username, req.role(), TenantContext.get());
        auditService.log(auth.getName(), "USER_ROLE_CHANGE", "/api/v1/users/" + username + "/role",
                "role=" + req.role(), resolveIp(httpRequest));
        return ResponseEntity.ok(updated);
    }

    @PatchMapping("/{username}/password")
    @Operation(summary = "Reset a user's password (cannot reset your own)")
    public ResponseEntity<Void> resetPassword(
            @PathVariable String username,
            @Valid @RequestBody ResetPasswordRequest req,
            Authentication auth,
            HttpServletRequest httpRequest) {
        if (auth.getName().equals(username)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cannot reset your own password");
        }
        userService.resetPassword(username, req.newPassword(), TenantContext.get());
        auditService.log(auth.getName(), "USER_PASSWORD_RESET", "/api/v1/users/" + username + "/password",
                null, resolveIp(httpRequest));
        return ResponseEntity.noContent().build();
    }

    private String resolveIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        return (forwarded != null) ? forwarded.split(",")[0].trim() : request.getRemoteAddr();
    }
}
