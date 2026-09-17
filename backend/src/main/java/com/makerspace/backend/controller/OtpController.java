package com.makerspace.backend.controller;

import com.makerspace.backend.config.security.OAuthProfile;
import com.makerspace.backend.model.User;
import com.makerspace.backend.model.UserResolution;
import com.makerspace.backend.services.JwtService;
import com.makerspace.backend.services.OtpService;
import com.makerspace.backend.services.UserService;
import com.makerspace.backend.services.UserStateService;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/auth/otp")
public class OtpController {

    @Autowired private OtpService otpService;
    @Autowired private UserService userService;
    @Autowired private UserStateService userStateService;
    @Autowired private JwtService jwtService;

    @Value("${app.jwt.expiration:3600000}")
    private int jwtExpiration;

    /**
     * Sends a 6-digit OTP to the provided email.
     * Rate-limited to one send per 60 seconds per email.
     */
    @PostMapping("/send")
    public ResponseEntity<Void> send(@RequestBody Map<String, String> body) {
        String email = body.get("email");
        if (email == null || email.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "email is required");
        }
        otpService.sendCode(email.trim().toLowerCase());
        return ResponseEntity.ok().build();
    }

    /**
     * Verifies the OTP for the given email.
     * On success: provisions or resolves the user, issues a JWT cookie, and returns the token.
     */
    @PostMapping("/verify")
    public ResponseEntity<Map<String, String>> verify(@RequestBody Map<String, String> body,
                                                       HttpServletResponse response) {
        String email = body.get("email");
        String code  = body.get("code");

        if (email == null || email.isBlank() || code == null || code.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "email and code are required");
        }

        email = email.trim().toLowerCase();

        if (!otpService.verifyCode(email, code)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "invalid_or_expired_code");
        }

        // OTP proves email ownership — reuse the same OAuth resolution logic
        String otpSubject = "otp:" + email;
        OAuthProfile profile = new OAuthProfile(email, email, "", otpSubject);
        UserResolution resolution = userService.resolve(profile);

        User user = switch (resolution) {
            case UserResolution.Active active -> active.user();

            case UserResolution.Pending pending ->
                // Email verified — auto-activate the pre-registered account
                userStateService.autoClaimByEmail(pending.user().getId(), otpSubject);

            case UserResolution.NotFound notFound ->
                // Self-provision as GUEST (same policy as OAuth2 flow)
                userService.provision(notFound.profile());

            case UserResolution.Deleted ignored ->
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "account_closed");
        };

        String token = jwtService.generateToken(user, otpSubject);

        Cookie cookie = new Cookie("access_token", token);
        cookie.setHttpOnly(true);
        cookie.setSecure(true);
        cookie.setPath("/");
        cookie.setMaxAge(jwtExpiration / 1000);
        cookie.setAttribute("SameSite", "Lax");
        response.addCookie(cookie);

        return ResponseEntity.ok(Map.of("access_token", token));
    }
}
