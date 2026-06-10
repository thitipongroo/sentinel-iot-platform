package com.sentinel.iot;

import com.sentinel.iot.dto.CreateUserRequest;
import com.sentinel.iot.dto.UserResponse;
import com.sentinel.iot.model.AppUser;
import com.sentinel.iot.repository.AppUserRepository;
import com.sentinel.iot.service.UserService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock AppUserRepository userRepository;
    @Mock PasswordEncoder passwordEncoder;

    @InjectMocks UserService service;

    private final UUID orgId   = UUID.fromString("aaaaaaaa-0000-0000-0000-000000000001");
    private final UUID otherOrg = UUID.fromString("bbbbbbbb-0000-0000-0000-000000000002");

    // ---- findAll -----------------------------------------------------------

    @SuppressWarnings("null")
    @Test
    void findAll_withOrgId_queriesByOrganization() {
        AppUser u = new AppUser("alice", "hash", "OPERATOR", orgId);
        when(userRepository.findAllByOrganizationId(orgId)).thenReturn(List.of(u));

        List<UserResponse> result = service.findAll(orgId);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).username()).isEqualTo("alice");
        assertThat(result.get(0).role()).isEqualTo("OPERATOR");
        verify(userRepository).findAllByOrganizationId(orgId);
        verify(userRepository, never()).findAll();
    }

    @Test
    void findAll_withNullOrgId_queriesAll() {
        when(userRepository.findAll()).thenReturn(List.of());

        service.findAll(null);

        verify(userRepository).findAll();
        verify(userRepository, never()).findAllByOrganizationId(any());
    }

    @SuppressWarnings("null")
    @Test
    void findAll_mapsToUserResponse_withCorrectFields() {
        UUID userId = UUID.randomUUID();
        AppUser u = new AppUser("bob", "hash", "ADMIN", orgId);
        u.setId(userId);
        when(userRepository.findAllByOrganizationId(orgId)).thenReturn(List.of(u));

        List<UserResponse> result = service.findAll(orgId);

        UserResponse response = result.get(0);
        assertThat(response.id()).isEqualTo(userId);
        assertThat(response.username()).isEqualTo("bob");
        assertThat(response.role()).isEqualTo("ADMIN");
    }

    // ---- create ------------------------------------------------------------

    @SuppressWarnings("null")
    @Test
    void create_savesUser_andReturnsResponse() {
        when(userRepository.findByUsername("alice")).thenReturn(Optional.empty());
        when(passwordEncoder.encode("password123")).thenReturn("encoded-hash");
        AppUser saved = new AppUser("alice", "encoded-hash", "OPERATOR", orgId);
        saved.setId(UUID.randomUUID());
        when(userRepository.save(any())).thenReturn(saved);

        UserResponse result = service.create(new CreateUserRequest("alice", "password123", "OPERATOR"), orgId);

        assertThat(result.username()).isEqualTo("alice");
        assertThat(result.role()).isEqualTo("OPERATOR");
        verify(passwordEncoder).encode("password123");
    }

    @SuppressWarnings("null")
    @Test
    void create_throwsConflict_whenUsernameAlreadyExists() {
        AppUser existing = new AppUser("alice", "hash", "ADMIN", orgId);
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> service.create(
                new CreateUserRequest("alice", "password123", "OPERATOR"), orgId))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(e -> ((ResponseStatusException) e).getStatusCode())
                .isEqualTo(HttpStatus.CONFLICT);
    }

    // ---- delete ------------------------------------------------------------

    @SuppressWarnings("null")
    @Test
    void delete_removesUser_whenFoundInSameOrg() {
        AppUser user = new AppUser("alice", "hash", "OPERATOR", orgId);
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(user));

        service.delete("alice", orgId);

        verify(userRepository).delete(user);
    }

    @SuppressWarnings("null")
    @Test
    void delete_throws404_whenUserNotFound() {
        when(userRepository.findByUsername(anyString())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.delete("ghost", orgId))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(e -> ((ResponseStatusException) e).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @SuppressWarnings("null")
    @Test
    void delete_throws404_whenUserBelongsToDifferentOrg() {
        AppUser user = new AppUser("alice", "hash", "OPERATOR", otherOrg);
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> service.delete("alice", orgId))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(e -> ((ResponseStatusException) e).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
        verify(userRepository, never()).delete(any());
    }

    // ---- resetPassword -----------------------------------------------------

    @SuppressWarnings("null")
    @Test
    void resetPassword_encodesAndSavesNewPassword() {
        AppUser user = new AppUser("alice", "old-hash", "OPERATOR", orgId);
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(user));
        when(passwordEncoder.encode("newPass123")).thenReturn("new-hash");

        service.resetPassword("alice", "newPass123", orgId);

        assertThat(user.getPassword()).isEqualTo("new-hash");
        verify(userRepository).save(user);
    }

    @SuppressWarnings("null")
    @Test
    void resetPassword_throws404_whenUserBelongsToDifferentOrg() {
        AppUser user = new AppUser("alice", "hash", "OPERATOR", otherOrg);
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> service.resetPassword("alice", "newPass", orgId))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(e -> ((ResponseStatusException) e).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
        verify(userRepository, never()).save(any());
    }

    // ---- changeRole --------------------------------------------------------

    @SuppressWarnings("null")
    @Test
    void changeRole_updatesRoleAndReturnsResponse() {
        AppUser user = new AppUser("alice", "hash", "OPERATOR", orgId);
        user.setId(UUID.randomUUID());
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(user));
        when(userRepository.save(any())).thenReturn(user);

        UserResponse result = service.changeRole("alice", "ADMIN", orgId);

        assertThat(result.role()).isEqualTo("ADMIN");
        assertThat(user.getRole()).isEqualTo("ADMIN");
        verify(userRepository).save(user);
    }

    @SuppressWarnings("null")
    @Test
    void changeRole_throws404_whenUserBelongsToDifferentOrg() {
        AppUser user = new AppUser("alice", "hash", "OPERATOR", otherOrg);
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> service.changeRole("alice", "ADMIN", orgId))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(e -> ((ResponseStatusException) e).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
        verify(userRepository, never()).save(any());
    }

    @SuppressWarnings("null")
    @Test
    void crossOrgGuard_isSkipped_whenOrgIdIsNull() {
        AppUser user = new AppUser("alice", "hash", "OPERATOR", otherOrg);
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(user));
        when(userRepository.save(any())).thenReturn(user);

        // null orgId means no tenant isolation (super-admin context) — should succeed
        service.changeRole("alice", "ADMIN", null);

        verify(userRepository).save(user);
    }
}
