package com.makerspace.backend.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Getter
@Setter
@Table(
    name = "equipment",
    indexes = {
        @Index(name = "idx_equipment_status",   columnList = "status"),
        @Index(name = "idx_equipment_category", columnList = "category")
    }
)
public class Equipment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    private String description;

    private String category;

    @Column(name = "image_url")
    private String imageUrl;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private EquipmentStatus status = EquipmentStatus.AVAILABLE;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    // ── Pricing ──────────────────────────────────────────────────────────────────

    /** Single rate in cents per hour for paying users, before free-access exemptions. */
    @Column(name = "hourly_rate_cents", nullable = false)
    private int hourlyRateCents = 0;

    @Transient
    private Integer resolvedHourlyRateCents;

    /** Legacy column kept in sync with the single hourly rate on create/update. */
    @Column(name = "member_rate_cents", nullable = false)
    private int memberRateCents = 0;

    /**
     * True for basic hand tools and workbench space that are included with membership
     * at no per-hour charge. Day-pass holders still pay the canonical hourly rate.
     */
    @Column(name = "free_with_membership", nullable = false)
    private boolean freeWithMembership = false;

    // ── Training prerequisite ─────────────────────────────────────────────────────

    /** True if a one-time training session must be completed before the first rental. */
    @Column(name = "requires_training", nullable = false)
    private boolean requiresTraining = false;

    /** True if staff must approve a signed waiver before the user can check in via NFC. */
    @Column(name = "requires_waiver", nullable = false)
    private boolean requiresWaiver = false;

    /** One-time training session fee in cents (0 when requiresTraining is false). */
    @Column(name = "training_fee_cents", nullable = false)
    private int trainingFeeCents = 0;

    /** YouTube or other URL for the training video shown to users after booking. */
    @Column(name = "training_video_url")
    private String trainingVideoUrl;

    // ── Waiver versioning ─────────────────────────────────────────────────────────

    /**
     * Current version of the safety waiver for this equipment. A waiver signed with
     * an older version is rejected at booking time, forcing the user to re-sign.
     * Increment this when the waiver text changes.
     */
    @Column(name = "current_waiver_version", nullable = false)
    private int currentWaiverVersion = 1;

    @Column(name = "stripe_product_id", length = 255)
    private String stripeProductId;
}
