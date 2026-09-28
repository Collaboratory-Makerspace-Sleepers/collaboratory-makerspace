package com.makerspace.backend.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.ZonedDateTime;

@Entity
@Getter
@Setter
@Table(
    name = "membership",
    indexes = {
        @Index(name = "idx_membership_user",          columnList = "user_id"),
        @Index(name = "idx_membership_status_period", columnList = "status, current_period_end")
    }
)
public class Membership {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "user_id", insertable = false, updatable = false)
    private Long userId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "plan_id", nullable = false)
    private MembershipPlan plan;

    @Column(name = "plan_id", insertable = false, updatable = false)
    private Long planId;

    @Column(name = "stripe_subscription_id", unique = true)
    private String stripeSubscriptionId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private MembershipStatus status;

    @Column(name = "current_period_start")
    private ZonedDateTime currentPeriodStart;

    @Column(name = "current_period_end")
    private ZonedDateTime currentPeriodEnd;

    @Column(name = "cancel_at_period_end", nullable = false)
    private boolean cancelAtPeriodEnd = false;

    @Column(name = "canceled_at")
    private ZonedDateTime canceledAt;

    @Column(name = "grace_period_ends_at")
    private ZonedDateTime gracePeriodEndsAt;

    @Column(name = "stripe_updated_at")
    private ZonedDateTime stripeUpdatedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private ZonedDateTime createdAt = ZonedDateTime.now();

    @Column(name = "updated_at", nullable = false)
    private ZonedDateTime updatedAt = ZonedDateTime.now();
}
