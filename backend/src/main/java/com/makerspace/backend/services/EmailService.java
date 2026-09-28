package com.makerspace.backend.services;

public interface EmailService {

    /**
     * Sends an invite link to a pre-registered user.
     * The raw token is embedded in the link — never log it.
     */
    void sendRegistrationInvite(String toEmail, String fullName, String rawToken);

    /**
     * Sends a one-time passcode to the given email address.
     * The code is short-lived (10 minutes) and single-use.
     */
    void sendOtp(String toEmail, String code);

    /**
     * Sends a reservation confirmation to the user with equipment and time details.
     */
    void sendReservationConfirmation(String toEmail, String fullName,
            String equipmentName, java.time.ZonedDateTime startTime, java.time.ZonedDateTime endTime);

    /**
     * Sends a payment receipt after a successful charge.
     */
    void sendPaymentReceipt(String toEmail, String fullName,
            String description, int amountCents, String currency);
}