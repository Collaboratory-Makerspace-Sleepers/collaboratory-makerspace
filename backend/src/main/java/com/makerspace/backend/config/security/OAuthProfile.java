package com.makerspace.backend.config.security;

import org.springframework.security.oauth2.core.oidc.user.OidcUser;

public record OAuthProfile(
        String email,
        String firstName,
        String lastName,
        String subject  // OAuth provider's stable user ID (the "sub" claim)
) {
    public static OAuthProfile from(OidcUser oidcUser) {
        // Apple does not return given_name/family_name on repeat logins — fall back gracefully
        String firstName = oidcUser.getGivenName() != null
                ? oidcUser.getGivenName()
                : oidcUser.getEmail();
        String lastName = oidcUser.getFamilyName() != null
                ? oidcUser.getFamilyName()
                : "";
        return new OAuthProfile(
                oidcUser.getEmail(),
                firstName,
                lastName,
                oidcUser.getSubject()
        );
    }
}