package com.makerspace.backend.config.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.lang.NonNull;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.preauth.PreAuthenticatedAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;

/**
 * Authenticates inbound requests from hardware devices (NFC readers, door controllers)
 * using a shared secret header.
 *
 * Hardware devices send {@code X-Device-Key: <secret>}. The secret is compared
 * in constant time against {@code app.hardware.device-key}.
 *
 * On success, the security context receives the {@code SCOPE_HARDWARE} authority,
 * which is required by {@code HardwareCheckinController}.
 */
@Component
public class DeviceKeyFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-Device-Key";

    @Value("${app.hardware.device-key:}")
    private String deviceKey;

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain chain)
            throws ServletException, IOException {

        String header = request.getHeader(HEADER);
        if (header == null || deviceKey.isEmpty()) {
            chain.doFilter(request, response);
            return;
        }

        boolean valid = MessageDigest.isEqual(
                deviceKey.getBytes(StandardCharsets.UTF_8),
                header.getBytes(StandardCharsets.UTF_8));

        if (!valid) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            return;
        }

        var auth = new PreAuthenticatedAuthenticationToken(
                "hardware-device", null,
                List.of(new SimpleGrantedAuthority("SCOPE_HARDWARE")));
        SecurityContextHolder.getContext().setAuthentication(auth);
        chain.doFilter(request, response);
    }
}
