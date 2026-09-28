package com.makerspace.backend.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.ZonedDateTime;

@Entity
@Getter
@Setter
@Table(name = "membership_plan")
public class MembershipPlan {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "code", unique = true, nullable = false, length = 50)
    private String code;

    @Column(name = "display_name", nullable = false, length = 100)
    private String displayName;

    @Column(name = "stripe_price_id", unique = true, nullable = false)
    private String stripePriceId;

    @Column(name = "stripe_product_id", nullable = false)
    private String stripeProductId;

    @Column(name = "billing_interval", length = 20)
    private String billingInterval;

    @Column(name = "amount_cents", nullable = false)
    private int amountCents;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency = "usd";

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "grants_role")
    private AppRole grantsRole;

    @Column(name = "active", nullable = false)
    private boolean active = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private ZonedDateTime createdAt = ZonedDateTime.now();
}
