package com.sentinel.iot;

import com.sentinel.iot.model.AppUser;
import com.sentinel.iot.repository.AppUserRepository;
import com.sentinel.iot.service.UserDetailsServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@Tag("unit")
@DisplayName("UserDetailsServiceImpl")
@ExtendWith(MockitoExtension.class)
class UserDetailsServiceImplTest {

    @Mock
    AppUserRepository userRepository;

    UserDetailsServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new UserDetailsServiceImpl(userRepository);
    }

    @Nested
    @DisplayName("loadUserByUsername")
    class LoadUserByUsername {

        @Test
        @DisplayName("returns UserDetails with correct username, password, and ROLE_ authority when user is found")
        void found_returnsUserDetails() {
            AppUser user = new AppUser("admin", "encoded-pass", "ADMIN", UUID.randomUUID());
            when(userRepository.findByUsername("admin")).thenReturn(Optional.of(user));

            UserDetails details = service.loadUserByUsername("admin");

            assertThat(details.getUsername())
                    .as("username must match the stored user")
                    .isEqualTo("admin");
            assertThat(details.getPassword())
                    .as("password must be the stored encoded password")
                    .isEqualTo("encoded-pass");
            assertThat(details.getAuthorities())
                    .as("authority must be ROLE_ADMIN")
                    .extracting(a -> a.getAuthority())
                    .containsExactly("ROLE_ADMIN");
        }

        @Test
        @DisplayName("throws UsernameNotFoundException with the missing username in the message when user is not found")
        void notFound_throwsUsernameNotFoundException() {
            when(userRepository.findByUsername("unknown")).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.loadUserByUsername("unknown"))
                    .isInstanceOf(UsernameNotFoundException.class)
                    .hasMessageContaining("unknown");
        }
    }
}
