package com.makerspace.backend.config.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.lang.NonNull;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.web.authentication.preauth.PreAuthenticatedAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;

/**
 * Authenticates inbound requests from the Lambda layer using a shared secret header.
 *
 * Must run before JwtAuthFilter so that internal requests are authenticated before
 * the JWT filter (which passes through on missing Bearer tokens) has a chance to
 * leave the context empty.
 *
 * The principal is deliberately NOT a UserPrincipal — UserSecurity.isSelf() and
 * getUserId() fail closed on non-UserPrincipal principals, which is the desired
 * behavior for machine-to-machine internal calls.
 *
 * TODO: Upgrade to SigV4 with IAM role verification for zero-shared-secret auth.
 */
@Component
public class InternalAuthFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-Internal-Secret";

    @Value("${app.internal.api.secret}")
    private String secret;

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain chain)
            throws ServletException, IOException {

        String header = request.getHeader(HEADER);
        if (header == null) {
            chain.doFilter(request, response);
            return;
        }

        // Constant-time comparison — String.equals() is timing-vulnerable.
        boolean valid = MessageDigest.isEqual(
                secret.getBytes(StandardCharsets.UTF_8),
                header.getBytes(StandardCharsets.UTF_8));

        if (!valid) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            return;
        }

        var auth = new PreAuthenticatedAuthenticationToken(
                "internal", null,
                List.of(new SimpleGrantedAuthority("SCOPE_INTERNAL")));
        SecurityContextHolder.getContext().setAuthentication(auth);
        chain.doFilter(request, response);
    }
}
