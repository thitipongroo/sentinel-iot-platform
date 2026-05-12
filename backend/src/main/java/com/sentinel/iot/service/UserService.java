package com.sentinel.iot.service;

import com.sentinel.iot.dto.CreateUserRequest;
import com.sentinel.iot.dto.UserResponse;
import com.sentinel.iot.model.AppUser;
import com.sentinel.iot.repository.AppUserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UserService {

    private final AppUserRepository userRepository;
    private final PasswordEncoder   passwordEncoder;

    public List<UserResponse> findAll(UUID orgId) {
        List<AppUser> users = (orgId != null)
                ? userRepository.findAllByOrganizationId(orgId)
                : userRepository.findAll();
        return users.stream()
                .map(u -> new UserResponse(u.getId(), u.getUsername(), u.getRole()))
                .toList();
    }

    public UserResponse create(CreateUserRequest req, UUID orgId) {
        if (userRepository.findByUsername(req.username()).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Username already exists");
        }
        AppUser user = new AppUser(
                req.username(),
                passwordEncoder.encode(req.password()),
                req.role(),
                orgId
        );
        AppUser saved = userRepository.save(user);
        return new UserResponse(saved.getId(), saved.getUsername(), saved.getRole());
    }

    public void delete(String username, UUID orgId) {
        AppUser user = userRepository.findByUsername(username)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
        if (orgId != null && !orgId.equals(user.getOrganizationId())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found");
        }
        userRepository.delete(user);
    }

    public void resetPassword(String username, String newPassword, UUID orgId) {
        AppUser user = userRepository.findByUsername(username)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
        if (orgId != null && !orgId.equals(user.getOrganizationId())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found");
        }
        user.setPassword(passwordEncoder.encode(newPassword));
        userRepository.save(user);
    }

    public UserResponse changeRole(String username, String newRole, UUID orgId) {
        AppUser user = userRepository.findByUsername(username)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
        if (orgId != null && !orgId.equals(user.getOrganizationId())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found");
        }
        user.setRole(newRole);
        AppUser saved = userRepository.save(user);
        return new UserResponse(saved.getId(), saved.getUsername(), saved.getRole());
    }
}
