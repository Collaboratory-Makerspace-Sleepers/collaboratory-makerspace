package com.makerspace.backend.config.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

@Component("userSecurity")
public class UserSecurity {

    private static final Logger log = LoggerFactory.getLogger(UserSecurity.class);

    /**
     * Returns true if the authenticated user's ID matches the given id.
     * Used in @PreAuthorize SpEL expressions for ownership-based access control.
     * Identity is read from the typed UserPrincipal that JwtAuthenticationFilter
     * places on the SecurityContext after validating the JWT. If the principal
     * is missing or the wrong type, this method fails closed (returns false) —
     * which means the @PreAuthorize check fails and Spring returns 403.
     */
    public boolean isSelf(Long id, Authentication auth) {
        if (id == null) {
            return false;
        }
        Long authenticatedId = getUserId(auth);
        return authenticatedId != null && authenticatedId.equals(id);
    }

    /**
     * Extracts the authenticated user's DB id from the SecurityContext.
     * Returns null if no valid principal is present
     * Controllers and services should prefer this over reading auth.getName()
     * or parsing the principal themselves.
     */
    public Long getUserId(Authentication auth) {
        UserPrincipal up = getPrincipal(auth);
        return up != null ? up.userId() : null;
    }

    /**
     * Extracts the typed UserPrincipal from the SecurityContext.
     * Returns null if authentication is absent or the principal is the wrong type.
     * Wrong type means the JWT filter is misconfigured, an unauthenticated request
     * slipped past the filter chain, or a test is setting up the SecurityContext
     * incorrectly — logged loudly and fails closed.
     */
    public UserPrincipal getPrincipal(Authentication auth) {
        if (auth == null) {
            return null;
        }
        Object principal = auth.getPrincipal();
        if (principal instanceof UserPrincipal up) {
            return up;
        }
        log.warn("Authentication principal is not a UserPrincipal: type={}",
                principal == null ? "null" : principal.getClass().getName());
        return null;
    }
}