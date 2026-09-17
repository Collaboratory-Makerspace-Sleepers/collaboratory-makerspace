package com.makerspace.backend.services;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.util.concurrent.TimeUnit;

/**
 * Issues and validates single-use one-time passcodes for email/OTP login.
 * Codes are stored in-memory keyed by (normalized) email and expire after 10 minutes.
 */
@Service
public class OtpService {

    private static final int CODE_LENGTH = 6;
    private static final long TTL_MINUTES = 10;

    private final SecureRandom random = new SecureRandom();
    private final Cache<String, String> codes = Caffeine.newBuilder()
            .expireAfterWrite(TTL_MINUTES, TimeUnit.MINUTES)
            .maximumSize(10_000)
            .build();

    @Autowired
    private EmailService emailService;

    /**
     * Generates a fresh 6-digit code for the given email, replacing any previous one,
     * and dispatches it via email.
     */
    public void sendCode(String email) {
        String code = String.format("%0" + CODE_LENGTH + "d", random.nextInt(1_000_000));
        codes.put(email, code);
        emailService.sendOtp(email, code);
    }

    /**
     * Validates the code for the email. The code is consumed on first use,
     * whether or not it matches, so a code can only be attempted once.
     */
    public boolean verifyCode(String email, String code) {
        String stored = codes.getIfPresent(email);
        codes.invalidate(email);
        return stored != null && stored.equals(code);
    }
}