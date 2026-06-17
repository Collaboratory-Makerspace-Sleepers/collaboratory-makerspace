package com.makerspace.backend.security;

import com.makerspace.backend.config.security.UserPrincipal;
import com.makerspace.backend.services.JwtService;
import com.makerspace.backend.services.UserStateService;
import com.makerspace.backend.services.UserStateService.State;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

@Component
public class JwtAuthFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final UserStateService userStateService;

    public JwtAuthFilter(JwtService jwtService, UserStateService userStateService) {
        this.jwtService = jwtService;
        this.userStateService = userStateService;
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain chain)
            throws ServletException, IOException {

        String token = extractBearerToken(request);

        if (token == null || !jwtService.isValid(token)) {
            chain.doFilter(request, response);
            return;
        }

        Claims claims = jwtService.parseToken(token);
        String email = claims.get("email", String.class);
        String auth0Subject = claims.get("auth0Subject", String.class);

        State state = userStateService.stateOf(email);

        switch (state) {
            case ACTIVE, PENDING -> {
                Long userId = Long.parseLong(claims.getSubject());
                List<?> rawRoles = claims.get("roles", List.class);
                List<GrantedAuthority> authorities = rawRoles == null ? List.of() :
                        rawRoles.stream()
                                .map(r -> (GrantedAuthority) UserPrincipal.roleAuthority(r.toString()))
                                .toList();
                // PENDING accounts carry no roles from DB but may have ROLE_PENDING from the JWT.
                // If the JWT roles list is empty for a PENDING account, add ROLE_PENDING so
                // Spring Security allows access only to /claim and /me.
                List<GrantedAuthority> effectiveAuthorities = (state == State.PENDING && authorities.isEmpty())
                        ? List.of(UserPrincipal.roleAuthority("PENDING"))
                        : authorities;
                var principal = new UserPrincipal(userId, auth0Subject, email, effectiveAuthorities);
                var auth = new UsernamePasswordAuthenticationToken(
                        principal, null, principal.authorities());
                SecurityContextHolder.getContext().setAuthentication(auth);
                chain.doFilter(request, response);
            }
            case DELETED -> {
                response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                response.setContentType("application/json");
                response.getWriter().write(
                        "{\"error\":\"account_closed\",\"message\":\"This account has been closed.\"}");
            }
            case NOT_FOUND -> {
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                response.setContentType("application/json");
                response.getWriter().write("{\"error\":\"unknown_user\"}");
            }
        }
    }

    private String extractBearerToken(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            return header.substring(7);
        }
        return null;
    }
}