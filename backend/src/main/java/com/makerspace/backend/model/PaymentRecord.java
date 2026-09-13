package com.makerspace.backend.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.ZonedDateTime;

@Entity
@Getter
@Setter
@Table(
    name = "payment_record",
    indexes = {
        @Index(name = "idx_payment_record_user",    columnList = "user_id, occurred_at"),
        @Index(name = "idx_payment_record_invoice", columnList = "stripe_invoice_id")
    }
)
public class PaymentRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    @Column(name = "user_id", insertable = false, updatable = false)
    private Long userId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "membership_id")
    private Membership membership;

    @Column(name = "membership_id", insertable = false, updatable = false)
    private Long membershipId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reservation_id")
    private EquipmentReservation reservation;

    @Column(name = "reservation_id", insertable = false, updatable = false)
    private Long reservationId;

    @Column(name = "stripe_payment_intent_id", unique = true)
    private String stripePaymentIntentId;

    @Column(name = "stripe_invoice_id")
    private String stripeInvoiceId;

    @Column(name = "stripe_charge_id")
    private String stripeChargeId;

    @Enumerated(EnumType.STRING)
    @Column(name = "kind", nullable = false, length = 30)
    private PaymentKind kind;

    @Column(name = "amount_cents", nullable = false)
    private int amountCents;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency = "usd";

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private PaymentStatus status;

    @Column(name = "description", length = 255)
    private String description;

    @Column(name = "occurred_at", nullable = false)
    private ZonedDateTime occurredAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private ZonedDateTime createdAt = ZonedDateTime.now();
}
