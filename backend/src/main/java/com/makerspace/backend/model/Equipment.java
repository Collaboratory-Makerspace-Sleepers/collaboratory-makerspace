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

    @Column(name = "training_required", nullable = false)
    private boolean trainingRequired;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private EquipmentStatus status = EquipmentStatus.AVAILABLE;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    // ── Pricing ──────────────────────────────────────────────────────────────────

    /** Rate in cents per hour charged to non-members (day-pass holders). */
    @Column(name = "hourly_rate_cents", nullable = false)
    private int hourlyRateCents = 0;

    /**
     * Rate in cents per hour charged to active members.
     * Seeded equal to hourlyRateCents until the client confirms the member discount.
     * OPEN: awaiting client decision on member discount percentage.
     */
    @Column(name = "member_rate_cents", nullable = false)
    private int memberRateCents = 0;

    /**
     * True for basic hand tools and workbench space that are included with membership
     * at no per-hour charge. Both hourlyRateCents and memberRateCents are 0 for these.
     * OPEN: awaiting client confirmation of which categories qualify.
     */
    @Column(name = "free_with_membership", nullable = false)
    private boolean freeWithMembership = false;

    // ── Training prerequisite ─────────────────────────────────────────────────────

    /** True if a one-time training session must be completed before the first rental. */
    @Column(name = "requires_training", nullable = false)
    private boolean requiresTraining = false;

    /** One-time training session fee in cents (0 when requiresTraining is false). */
    @Column(name = "training_fee_cents", nullable = false)
    private int trainingFeeCents = 0;

}

