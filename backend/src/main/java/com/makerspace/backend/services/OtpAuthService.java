package com.makerspace.backend.services;

import com.makerspace.backend.model.User;
import com.makerspace.backend.repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Email one-time-passcode authentication.
 * The OTP proves control of the inbox, so the sign-in and sign-up share a
 * single flow: an unknown address is provisioned as a new GUEST account
 * (mirroring the OAuth2 auto-provision path).
 */
@Service
public class OtpAuthService {

    private final OtpService otpService;
    private final EmailService emailService;
    private final UserRepository userRepository;
    private final UserService userService;
    private final JwtService jwtService;

    public OtpAuthService(OtpService otpService,
                          EmailService emailService,
                          UserRepository userRepository,
                          UserService userService,
                          JwtService jwtService) {
        this.otpService = otpService;
        this.emailService = emailService;
        this.userRepository = userRepository;
        this.userService = userService;
        this.jwtService = jwtService;
    }

    /**
     * Generates a code for the address and dispatches it via email.
     */
    public void issueCode(String email) {
        String normalized = normalize(email);
        String code = otpService.issue(normalized);
        emailService.sendOtpCode(normalized, code);
    }

    /**
     * Validates the code and issues a JWT. Unknown addresses are provisioned as
     * new GUEST accounts; existing active or pre-registered accounts are reused.
     */
    @Transactional
    public Map<String, String> verifyAndLogin(String email, String code) {
        String normalized = normalize(email);
        if (!otpService.verify(normalized, code)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid or expired code");
        }
        User user = resolveOrProvision(normalized);
        return Map.of("access_token", jwtService.generateToken(user, null));
    }

    private User resolveOrProvision(String email) {
        Optional<User> existing = userRepository.findByEmailIncludingDeleted(email);
        if (existing.isPresent()) {
            User user = existing.get();
            if (user.getDeletedAt() != null) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "account_closed");
            }
            return user;
        }
        return userService.provisionByEmail(email);
    }

    private String normalize(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}