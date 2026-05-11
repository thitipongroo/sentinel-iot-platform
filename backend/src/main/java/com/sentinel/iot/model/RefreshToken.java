package com.sentinel.iot.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "refresh_tokens", indexes = {
    @Index(name = "idx_refresh_token_token",    columnList = "token"),
    @Index(name = "idx_refresh_token_username", columnList = "username")
})
@Data
@NoArgsConstructor
public class RefreshToken {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /**
     * SHA-256 hash of the raw token value — never the raw token itself.
     * A DB breach cannot be used to replay tokens because the hash is one-way.
     * The raw value is generated in {@link com.sentinel.iot.service.JwtService}
     * and returned to the caller via the transient {@link #rawToken} field;
     * it is never persisted.
     */
    @Column(nullable = false, unique = true, length = 64)
    private String token;

    /** Populated only immediately after token generation — never loaded from DB. */
    @Transient
    private String rawToken;

    @Column(nullable = false)
    private String username;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(nullable = false)
    private boolean revoked = false;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    public RefreshToken(String tokenHash, String username, Instant expiresAt) {
        this.token = tokenHash;
        this.username = username;
        this.expiresAt = expiresAt;
    }

    public boolean isExpired() {
        return Instant.now().isAfter(expiresAt);
    }
}
