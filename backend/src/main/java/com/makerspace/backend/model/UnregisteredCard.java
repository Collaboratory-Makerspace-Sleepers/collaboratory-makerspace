package com.makerspace.backend.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

/** Tracks card UIDs from unregistered/unknown cards for admin review and assignment. */
@Entity
@Table(name = "unregistered_cards")
@Getter
@Setter
public class UnregisteredCard {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "card_uid", nullable = false, unique = true, length = 32)
    private String cardUid;

    @Column(name = "first_scanned_at", nullable = false)
    private Instant firstScannedAt = Instant.now();

    @Column(name = "last_scanned_at", nullable = false)
    private Instant lastScannedAt = Instant.now();

    @Column(name = "assigned_to_email", length = 255)
    private String assignedToEmail;

    @Column(name = "assigned_at")
    private Instant assignedAt;

    @Column(name = "otp_code", length = 10)
    private String otpCode;

    @Column(name = "otp_generated_at")
    private Instant otpGeneratedAt;

    @Column(name = "otp_expires_at")
    private Instant otpExpiresAt;

    @Column(name = "otp_verified")
    private boolean otpVerified;

    @Column(name = "verified_at")
    private Instant verifiedAt;

    @Column(nullable = false, length = 50)
    private String status = "PENDING"; // PENDING, ASSIGNED, VERIFIED, ACTIVATED, REJECTED

    @Column(name = "device_id", length = 100)
    private String deviceId;

    @Column(name = "scan_count")
    private Integer scanCount = 1;
}
