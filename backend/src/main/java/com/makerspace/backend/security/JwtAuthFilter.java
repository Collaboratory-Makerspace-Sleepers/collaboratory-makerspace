package com.makerspace.backend.security;

import com.makerspace.backend.config.security.UserPrincipal;
import com.makerspace.backend.services.JwtService;
import com.makerspace.backend.services.UserPermissionService;
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
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.Set;

/**
 * Validates the JWT on every request and populates the SecurityContext.
 *
 * Authorities are permission codes loaded from the database (cached 30 s),
 * not role names with a hard-coded hierarchy. A request to
 * {@code hasAuthority("MANAGE_EQUIPMENT")} passes if the user's assigned
 * roles collectively include that permission code — regardless of which role
 * name carries it.
 *
 * PENDING accounts receive a single synthetic {@code ROLE_PENDING} authority
 * so they can reach the claim endpoint without any DB-backed permissions.
 */
@Component
public class JwtAuthFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final UserStateService userStateService;
    private final UserPermissionService userPermissionService;

    public JwtAuthFilter(JwtService jwtService,
                         UserStateService userStateService,
                         UserPermissionService userPermissionService) {
        this.jwtService = jwtService;
        this.userStateService = userStateService;
        this.userPermissionService = userPermissionService;
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
            case ACTIVE -> {
                Long userId = Long.parseLong(claims.getSubject());
                // Load permissions from DB (cached). Each permission code becomes a
                // GrantedAuthority so filter chains can use hasAuthority("MANAGE_*").
                Set<String> perms = userPermissionService.getEffectivePermissions(email);
                List<GrantedAuthority> authorities = perms.stream()
                        .map(p -> (GrantedAuthority) new SimpleGrantedAuthority(p))
                        .toList();
                var principal = new UserPrincipal(userId, auth0Subject, email, authorities);
                SecurityContextHolder.getContext().setAuthentication(
                        new UsernamePasswordAuthenticationToken(principal, null, authorities));
                chain.doFilter(request, response);
            }
            case PENDING -> {
                Long userId = Long.parseLong(claims.getSubject());
                // PENDING users get only ROLE_PENDING — enough to reach /claim and /me.
                List<GrantedAuthority> pending = List.of(UserPrincipal.roleAuthority("PENDING"));
                var principal = new UserPrincipal(userId, auth0Subject, email, pending);
                SecurityContextHolder.getContext().setAuthentication(
                        new UsernamePasswordAuthenticationToken(principal, null, pending));
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