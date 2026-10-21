package com.makerspace.backend.services;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Primary;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Real SMTP email service — activated when spring.mail.host is configured to a non-empty value.
 * Takes precedence over StubEmailService via @Primary. With no host configured
 * (local dev), StubEmailService logs the OTP/invite links to the console.
 * Configure via environment variables: MAIL_HOST, MAIL_PORT, MAIL_USERNAME, MAIL_PASSWORD, MAIL_FROM.
 * For local dev, point at Mailtrap sandbox (sandbox.smtp.mailtrap.io:2525).
 * For production, use Resend (smtp.resend.com:465/587) or SendGrid.
 */
@Slf4j
@Primary
@Service
@ConditionalOnExpression("'${spring.mail.host:}'.length() > 0")
public class SmtpEmailService implements EmailService {

    @Autowired
    private JavaMailSender mailSender;

    @Value("${app.frontend-base-url:http://localhost:5173}")
    private String frontendBaseUrl;

    @Value("${app.mail.from:noreply@makerspace.local}")
    private String fromAddress;

    @Override
    public void sendRegistrationInvite(String toEmail, String fullName, String rawToken) {
        String link = frontendBaseUrl + "/register/confirm?token=" + rawToken;
        SimpleMailMessage msg = new SimpleMailMessage();
        msg.setFrom(fromAddress);
        msg.setTo(toEmail);
        msg.setSubject("You've been invited to Collaboratory Makerspace");
        msg.setText("Hi " + fullName + ",\n\nClick the link below to activate your account:\n" + link
                + "\n\nThis link expires in 48 hours.");
        mailSender.send(msg);
        log.info("Registration invite sent to <{}>", toEmail);
    }

    @Override
    public void sendOtp(String toEmail, String code) {
        SimpleMailMessage msg = new SimpleMailMessage();
        msg.setFrom(fromAddress);
        msg.setTo(toEmail);
        msg.setSubject("Your Collaboratory sign-in code");
        msg.setText("Your sign-in code is: " + code + "\n\nThis code expires in 10 minutes. Do not share it.");
        mailSender.send(msg);
        log.info("OTP sent to <{}>", toEmail);
    }

    @Override
    public void sendReservationConfirmation(String toEmail, String fullName,
            String equipmentName, ZonedDateTime startTime, ZonedDateTime endTime) {
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("EEE, MMM d yyyy 'at' h:mm a z");
        SimpleMailMessage msg = new SimpleMailMessage();
        msg.setFrom(fromAddress);
        msg.setTo(toEmail);
        msg.setSubject("Reservation confirmed — " + equipmentName);
        msg.setText("Hi " + fullName + ",\n\n"
                + "Your reservation has been confirmed.\n\n"
                + "Equipment: " + equipmentName + "\n"
                + "Start:     " + startTime.format(fmt) + "\n"
                + "End:       " + endTime.format(fmt) + "\n\n"
                + "View or manage your reservations at: " + frontendBaseUrl + "/dashboard/reservations\n\n"
                + "— The Collaboratory Makerspace");
        mailSender.send(msg);
        log.info("Reservation confirmation sent to <{}>", toEmail);
    }

    @Override
    public void sendWaiverSigned(String toEmail, String fullName, String equipmentName,
            String signedName, byte[] pdf) {
        try {
            jakarta.mail.internet.MimeMessage msg = mailSender.createMimeMessage();
            org.springframework.mail.javamail.MimeMessageHelper helper =
                    new org.springframework.mail.javamail.MimeMessageHelper(msg, true, "UTF-8");
            helper.setFrom(fromAddress);
            helper.setTo(toEmail);
            helper.setSubject("Your signed waiver — " + equipmentName);
            helper.setText("Hi " + fullName + ",\n\nThanks for signing the waiver for " + equipmentName
                    + " (signed as " + signedName + "). A copy is attached as a PDF.\n\n"
                    + "Staff will review it shortly; you'll be able to continue once it's approved.\n\n"
                    + "— The Collaboratory Makerspace");
            helper.addAttachment("signed-waiver.pdf",
                    new org.springframework.core.io.ByteArrayResource(pdf), "application/pdf");
            mailSender.send(msg);
        } catch (jakarta.mail.MessagingException e) {
            throw new IllegalStateException("Could not build waiver email", e);
        }
        log.info("Signed waiver PDF sent to <{}>", toEmail);
    }

    @Override
    public void sendAccountInvite(String toEmail, String fullName, String roleLabel, String code) {
        SimpleMailMessage msg = new SimpleMailMessage();
        msg.setFrom(fromAddress);
        msg.setTo(toEmail);
        msg.setSubject("You've been added to Collaboratory Makerspace");
        msg.setText("Hi " + fullName + ",\n\nAn administrator created a " + roleLabel
                + " account for you.\n\nSign in at " + frontendBaseUrl + "/signin with this email address and the code:\n\n    "
                + code + "\n\nThe code is valid for 24 hours. After that, enter your email on the sign-in page and we'll send a new one.\n\n"
                + "— The Collaboratory Makerspace");
        mailSender.send(msg);
        log.info("Account invite sent to <{}>", toEmail);
    }

    @Override
    public void sendPaymentReceipt(String toEmail, String fullName,
            String description, int amountCents, String currency) {
        String amount = String.format("$%.2f %s", amountCents / 100.0, currency.toUpperCase());
        SimpleMailMessage msg = new SimpleMailMessage();
        msg.setFrom(fromAddress);
        msg.setTo(toEmail);
        msg.setSubject("Payment receipt — " + description);
        msg.setText("Hi " + fullName + ",\n\n"
                + "We received your payment of " + amount + " for: " + description + ".\n\n"
                + "Thank you for being a member of the Collaboratory Makerspace.\n\n"
                + "— The Collaboratory Makerspace");
        mailSender.send(msg);
        log.info("Payment receipt sent to <{}>", toEmail);
    }
}
