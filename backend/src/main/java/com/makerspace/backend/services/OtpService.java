package com.makerspace.backend.services;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.security.SecureRandom;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
public class OtpService {

    private static final int OTP_TTL_MINUTES = 10;
    private static final int RESEND_COOLDOWN_SECONDS = 60;

    private final SecureRandom random = new SecureRandom();

    @Autowired
    private StringRedisTemplate redis;

    // Local testing only: when set, this code is accepted for any email. Leave unset in production.
    @Value("${APP_OTP_DEV_CODE:}")
    private String devCode;

    @Autowired
    private EmailService emailService;

    private String codeKey(String email)     { return "otp:code:"     + email; }
    private String cooldownKey(String email) { return "otp:cooldown:" + email; }

    /**
     * Generates and sends a 6-digit OTP to the given email.
     * Rate-limited: throws 429 if called again within 60 seconds.
     */
    public void sendCode(String email) {
        if (redis.hasKey(cooldownKey(email))) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
                    "Please wait before requesting another code.");
        }

        String code = String.format("%06d", random.nextInt(1_000_000));
        redis.opsForValue().set(codeKey(email), code, OTP_TTL_MINUTES, TimeUnit.MINUTES);
        redis.opsForValue().set(cooldownKey(email), "1", RESEND_COOLDOWN_SECONDS, TimeUnit.SECONDS);

        try {
            emailService.sendOtp(email, code);
        } catch (RuntimeException e) {
            // With a dev code configured, a failed email must not block local sign-in.
            if (devCode.isBlank()) throw e;
            log.warn("OTP email to {} failed; dev code is active: {}", email, e.getMessage());
        }
    }

    /**
     * Returns true and consumes the code if it matches; false otherwise.
     * Consumed codes cannot be reused.
     */
    public boolean verifyCode(String email, String code) {
        if (!devCode.isBlank() && devCode.equals(code)) return true;
        String stored = redis.opsForValue().get(codeKey(email));
        if (stored == null || !stored.equals(code)) return false;
        redis.delete(codeKey(email));
        return true;
    }
}