package com.makerspace.backend.services;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.ZonedDateTime;

/**
 * Placeholder EmailService — logs the invite link and OTP instead of sending email.
 * Replace with an SES implementation once email transport is configured.
 * The raw token must never appear in production logs.
 * SES wiring: verify the sending domain and from-address, then implement sendRegistrationInvite
 * using software.amazon.awssdk:ses or spring-cloud-aws-ses.
 * TODO: implement notification service through AWS.
 */
@Slf4j
@Service
public class StubEmailService implements EmailService {

    @Value("${app.frontend-base-url:http://localhost:5173}")
    private String frontendBaseUrl;

    @Override
    public void sendRegistrationInvite(String toEmail, String fullName, String rawToken) {
        String link = frontendBaseUrl + "/register/confirm?token=" + rawToken;
        log.warn("[STUB] Registration invite for {} <{}> — link: {}", fullName, toEmail, link);
    }

    @Override
    public void sendOtp(String toEmail, String code) {
        log.warn("[STUB] OTP for <{}> — code: {}", toEmail, code);
    }

    @Override
    public void sendReservationConfirmation(String toEmail, String fullName,
            String equipmentName, ZonedDateTime startTime, ZonedDateTime endTime) {
        log.warn("[STUB] Reservation confirmation for {} <{}> — {} from {} to {}",
                fullName, toEmail, equipmentName, startTime, endTime);
    }

    @Override
    public void sendPaymentReceipt(String toEmail, String fullName,
            String description, int amountCents, String currency) {
        log.warn("[STUB] Payment receipt for {} <{}> — {}{} for {}",
                fullName, toEmail, currency.toUpperCase(), amountCents / 100.0, description);
    }
}