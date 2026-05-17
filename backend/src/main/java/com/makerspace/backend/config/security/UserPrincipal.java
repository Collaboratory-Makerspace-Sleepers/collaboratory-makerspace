package com.makerspace.backend.config.security;

import java.util.Collection;
import java.util.Collections;
import java.util.Objects;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

/**
 * Authenticated user principal stored on the SecurityContext.
 * <p>
 * Populated by JwtAuthenticationFilter after the JWT signature and claims have
 * been validated. Once set, this object is the trusted source of identity for
 * the request — controllers, services, and SpEL expressions should read from
 * here rather than reparsing tokens or headers.
 */
public record UserPrincipal(Long userId, String auth0Subject, String email, Collection<GrantedAuthority> authorities) {

    public UserPrincipal(Long userId,
                         String auth0Subject,
                         String email,
                         Collection<GrantedAuthority> authorities) {
        this.userId = Objects.requireNonNull(userId, "userId");
        this.auth0Subject = Objects.requireNonNull(auth0Subject, "auth0Subject");
        this.email = email;
        this.authorities = authorities == null
                ? Collections.emptyList()
                : Collections.unmodifiableCollection(authorities);
    }

    /**
     * Convenience for building authorities from a role enum.
     */
    public static GrantedAuthority roleAuthority(String role) {
        return new SimpleGrantedAuthority("ROLE_" + role);
    }

    @Override
    public String toString() {
        // No PII leakage: userId is fine, email is not.
        return "UserPrincipal{userId=" + userId + ", auth0Subject=" + auth0Subject + "}";
    }
}