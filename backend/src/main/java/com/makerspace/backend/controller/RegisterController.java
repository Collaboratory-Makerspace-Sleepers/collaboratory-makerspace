package com.makerspace.backend.controller;

import com.makerspace.backend.controller.dto.RegisterRequest;
import com.makerspace.backend.model.User;
import com.makerspace.backend.services.OtpService;
import com.makerspace.backend.services.UserService;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

/**
 * Public self-registration.
 *
 * The account is created unverified and no token is issued. The caller proves
 * control of the address with {@code POST /api/v1/auth/otp/{send,verify}}, which
 * is the same passwordless path OAuth2 sign-in uses. Handing back a token here
 * instead would mean anyone could register an address they do not own and
 * receive a session for it.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/auth")
public class RegisterController {

    @Autowired private UserService userService;
    @Autowired private OtpService otpService;

    @PostMapping("/register")
    public ResponseEntity<Map<String, Object>> register(@Valid @RequestBody RegisterRequest req) {
        log.info("Signup request received");
        User user;
        try {
            user = userService.preRegister(req.email(), req.firstName(), req.lastName().trim());
        } catch (ResponseStatusException e) {
            log.warn("Signup rejected (HTTP {}): {}", e.getStatusCode().value(), e.getReason());
            throw e;
        }

        // Best-effort: a failed send must not lose the account the caller just
        // created, and they can always request another code. Catches broadly on
        // purpose — the OTP store is Redis, so an outage there surfaces as a
        // connection failure rather than a ResponseStatusException.
        String delivery;
        try {
            otpService.sendCode(user.getEmail());
            delivery = "sent";
        } catch (ResponseStatusException e) {
            delivery = e.getStatusCode().value() == 429 ? "cooldown" : "unavailable";
        } catch (Exception e) {
            log.warn("Signup OTP delivery failed; check Redis and email service", e);
            delivery = "unavailable";
        }

        log.info("Signup accepted; OTP delivery status={}", delivery);

        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                "status", "verification_required",
                "email", user.getEmail(),
                "otpDelivery", delivery,
                "message", "Check your email for a verification code, then sign in with it."
        ));
    }
}
