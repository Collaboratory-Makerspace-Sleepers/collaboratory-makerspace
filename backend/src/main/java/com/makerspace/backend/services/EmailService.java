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
     * equipmentCategory is the raw category code (e.g. "3D_PRINTER"); the implementation
     * is responsible for rendering it as a human-readable label.
     */
    void sendReservationConfirmation(String toEmail, String fullName,
            String equipmentName, String equipmentCategory,
            java.time.ZonedDateTime startTime, java.time.ZonedDateTime endTime);

    void sendWaiverSigned(String toEmail, String fullName, String equipmentName,
            String signedName, byte[] pdf);

    void sendAccountInvite(String toEmail, String fullName, String roleLabel, String code);

    /**
     * Sends a payment receipt after a successful charge.
     */
    void sendPaymentReceipt(String toEmail, String fullName,
            String description, int amountCents, String currency);

    /**
     * Notifies the member that their subscription payment failed and prompts them
     * to update their payment method via the Stripe portal.
     * portalUrl may be null if the portal session could not be created.
     */
    void sendPaymentFailed(String toEmail, String fullName, String portalUrl);
}