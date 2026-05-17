package com.makerspace.backend.config.security;

import org.springframework.security.oauth2.core.oidc.user.OidcUser;

public record OAuthProfile(
        String email,
        String firstName,
        String lastName,
        String subject  // OAuth provider's stable user ID (the "sub" claim)
) {
    public static OAuthProfile from(OidcUser oidcUser) {
        String firstName = oidcUser.getGivenName() != null
                ? oidcUser.getGivenName()
                : oidcUser.getEmail();
        return new OAuthProfile(
                oidcUser.getEmail(),
                firstName,
                oidcUser.getFamilyName(),
                oidcUser.getSubject()
        );
    }
}