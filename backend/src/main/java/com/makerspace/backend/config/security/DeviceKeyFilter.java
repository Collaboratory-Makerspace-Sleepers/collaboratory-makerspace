package com.makerspace.backend.config.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import com.makerspace.backend.services.HardwareDeviceService;
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
 * using individual registered credentials, or an explicitly enabled legacy key.
 *
 * Paired Pis send X-Device-Id and X-Device-Key. Only hashes are stored. Legacy
 * readers without an ID can still use app.hardware.device-key for existing ingress.
 *
 * On success, the security context receives the {@code SCOPE_HARDWARE} authority,
 * which is required by {@code HardwareCheckinController}.
 */
@Component
public class DeviceKeyFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-Device-Key";

    @Value("${app.hardware.device-key:}")
    private String deviceKey;

    @Autowired private HardwareDeviceService deviceService;

    @Value("${app.hardware.legacy-device-key-enabled:true}")
    private boolean legacyEnabled;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = path(request);
        return !(path.equals("/api/v1/hardware/card-scan")
                || path.equals("/api/v1/hardware/checkin")
                || path.equals("/api/v1/hardware/device/heartbeat")
                || path.equals("/api/v1/hardware/device/events"));
    }

    private String path(HttpServletRequest request) {
        return request.getRequestURI().substring(request.getContextPath().length());
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain chain)
            throws ServletException, IOException {

        String header = request.getHeader(HEADER);
        String id = request.getHeader("X-Device-Id");
        if (id != null) {
            if (path(request).equals("/api/v1/hardware/checkin")) {
                response.sendError(HttpServletResponse.SC_FORBIDDEN, "Paired Pis must use card-scan with their configured area");
                return;
            }
            var principal = deviceService.authenticate(id, header);
            if (principal.isEmpty()) {
                response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Invalid Pi credentials");
                return;
            }
            var auth = new PreAuthenticatedAuthenticationToken(principal.get(), null,
                    List.of(new SimpleGrantedAuthority("SCOPE_HARDWARE")));
            SecurityContextHolder.getContext().setAuthentication(auth);
            chain.doFilter(request, response);
            return;
        }
        if (header == null) {
            chain.doFilter(request, response);
            return;
        }
        if (!legacyEnabled || deviceKey.isBlank() || path(request).startsWith("/api/v1/hardware/device/")) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Pair this Pi first");
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
