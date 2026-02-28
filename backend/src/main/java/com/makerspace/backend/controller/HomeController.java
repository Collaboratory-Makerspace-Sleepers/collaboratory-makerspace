package com.makerspace.backend.controller;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.Map;

import java.util.stream.Collectors;

@RestController
public class HomeController {

    @GetMapping("/")
    public String home(@AuthenticationPrincipal OidcUser user) {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        var authorities = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority).collect(Collectors.toSet());
        return "Hello, " + user.getFullName() + "!<br/><br/>Authorities: " + authorities;
    }

    @GetMapping("/admin")
    @PreAuthorize("hasAuthority('Administrator')")
    public String admin(@AuthenticationPrincipal OidcUser user) {
        return "Hello, Admin!<br/><br/><img src=" + user.getPicture() + " width=200/>";
    }

    @GetMapping("/profile")
    @PreAuthorize("hasAuthority('SCOPE_profile')")
    public Map<String, Object> profile(OAuth2AuthenticationToken authentication) {
        return authentication.getPrincipal().getAttributes();
    }

    @GetMapping("/jwt")
    @PreAuthorize("hasAuthority('Administrator')")
    public String jwt(@AuthenticationPrincipal Jwt jwt) {
        return String.format("Hello, %s!\nClaims: %s",
                jwt.getSubject(), jwt.getClaims());
    }

    @GetMapping("/profile-jwt")
    @PreAuthorize("hasAuthority('SCOPE_profile')")
    public Map<String, Object> profileJwt(@AuthenticationPrincipal Jwt jwt) {
        return jwt.getClaims();
    }
}
