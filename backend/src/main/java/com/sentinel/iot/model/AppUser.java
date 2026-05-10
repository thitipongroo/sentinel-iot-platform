package com.sentinel.iot.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Entity
@Table(name = "app_users")
@Data
@NoArgsConstructor
public class AppUser {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, unique = true)
    private String username;

    @Column(nullable = false)
    private String password;

    @Column(nullable = false)
    private String role; // ADMIN, OPERATOR

    @Column(name = "organization_id", nullable = false)
    private UUID organizationId;

    public AppUser(String username, String password, String role, UUID organizationId) {
        this.username = username;
        this.password = password;
        this.role = role;
        this.organizationId = organizationId;
    }
}
