package com.sentinel.iot;

import com.sentinel.iot.dto.ChangeRoleRequest;
import com.sentinel.iot.dto.CreateUserRequest;
import com.sentinel.iot.security.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@DisplayName("UserController — user CRUD and role-based access")
class UserControllerIntegrationTest extends BaseIntegrationTest {

    private static final UUID TEST_ORG = UUID.fromString("a0000000-0000-0000-0000-000000000001");

    @BeforeEach
    void setTenantContext() {
        TenantContext.set(TEST_ORG);
    }

    @AfterEach
    void clearTenantContext() {
        TenantContext.clear();
    }

    // ── List users ────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("List users")
    class ListUsers {

        @SuppressWarnings("null")
        @Test
        @WithMockUser(roles = "ADMIN")
        @DisplayName("ADMIN can list all users in the org")
        void findAll_adminRole_returns200() throws Exception {
            mockMvc.perform(get("/api/v1/users"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$").isArray());
        }

        @SuppressWarnings("null")
        @Test
        @WithMockUser(roles = "OPERATOR")
        @DisplayName("OPERATOR is forbidden from listing users")
        void findAll_operatorRole_returns403() throws Exception {
            mockMvc.perform(get("/api/v1/users"))
                    .andExpect(status().isForbidden());
        }
    }

    // ── Create user ───────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Create user")
    class CreateUser {

        @SuppressWarnings("null")
        @Test
        @WithMockUser(roles = "ADMIN")
        @DisplayName("ADMIN can create a new OPERATOR user and it is returned with 201")
        void create_adminRole_returns201() throws Exception {
            String uniqueName = "u-" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
            CreateUserRequest req = new CreateUserRequest(uniqueName, "password123", "OPERATOR");

            mockMvc.perform(post("/api/v1/users")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.username").value(uniqueName));
        }
    }

    // ── Self-protection guards ─────────────────────────────────────────────────

    @Nested
    @DisplayName("Self-protection guards")
    class SelfProtectionGuards {

        @SuppressWarnings("null")
        @Test
        @WithMockUser(username = "admin", roles = "ADMIN")
        @DisplayName("ADMIN cannot delete their own account")
        void delete_selfDelete_returns400() throws Exception {
            mockMvc.perform(delete("/api/v1/users/{username}", "admin"))
                    .andExpect(status().isBadRequest());
        }

        @SuppressWarnings("null")
        @Test
        @WithMockUser(username = "admin", roles = "ADMIN")
        @DisplayName("ADMIN cannot change their own role")
        void changeRole_selfChange_returns400() throws Exception {
            ChangeRoleRequest req = new ChangeRoleRequest("OPERATOR");

            mockMvc.perform(patch("/api/v1/users/{username}/role", "admin")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isBadRequest());
        }
    }
}
