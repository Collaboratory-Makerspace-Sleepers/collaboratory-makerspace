package com.makerspace.backend.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;


@Entity
@Getter
@Setter
@SQLDelete(sql = "UPDATE users SET deleted_at = CURRENT_TIMESTAMP WHERE id = ?")
@SQLRestriction("deleted_at IS NULL") // Adds WHERE deleted_at IS NULL to all SELECT statements
@Table(
    name = "users",
    indexes = {
        @Index(name = "idx_users_auth0_subject",  columnList = "auth0_subject"),
        @Index(name = "idx_users_account_status", columnList = "account_status"),
        @Index(name = "idx_users_deleted_at",     columnList = "deleted_at")
    }
)
public class User {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) @Column(name = "id")
    private Long id;

    // emailDigest is an HMAC of the email for privacy-safe lookup after account deletion.
    // The plain email is kept while the account is active and nulled on hard delete.
    @Column(unique = true, nullable = false)
    private String email;

    @Column(name = "email_digest", length = 64)
    private String emailDigest;

    /** Auth0 stable subject identifier (e.g. google-oauth2|123…). Null until the account is claimed. */
    @Column(name = "auth0_subject", unique = true)
    private String auth0Subject;

    @Enumerated(EnumType.STRING)
    @Column(name = "account_status", nullable = false)
    private AccountStatus accountStatus = AccountStatus.ACTIVE;

    @OneToOne(cascade = CascadeType.ALL, fetch = FetchType.EAGER, orphanRemoval = true)
    @JoinColumn(name = "profile_id", referencedColumnName = "id")
    private UserProfile profile;

    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(
        name = "user_roles",
        joinColumns = @JoinColumn(name = "user_id"),
        inverseJoinColumns = @JoinColumn(name = "role")
    )
    private Set<AppRole> roles = new HashSet<>();

    /** Convenience: the set of permission codes effective for this user. */
    public Set<String> effectivePermissions() {
        return roles.stream()
                .flatMap(r -> r.getPermissions().stream())
                .collect(Collectors.toSet());
    }

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt = null;
}