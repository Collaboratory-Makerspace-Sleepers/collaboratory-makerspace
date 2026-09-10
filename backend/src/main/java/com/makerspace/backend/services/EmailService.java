package com.makerspace.backend.services;

public interface EmailService {

    /**
     * Sends an invite link to a pre-registered user.
     * The raw token is embedded in the link — never log it.
     */
    void sendRegistrationInvite(String toEmail, String fullName, String rawToken);

    /**
     * Sends a one-time passcode for email/OTP login.
     */
    void sendOtpCode(String toEmail, String code);
}
