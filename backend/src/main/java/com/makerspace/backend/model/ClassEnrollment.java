package com.makerspace.backend.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.ZonedDateTime;

@Entity
@Getter
@Setter
@Table(
    name = "class_enrollment",
    indexes = {
        @Index(name = "idx_class_enrollment_user",    columnList = "user_id"),
        @Index(name = "idx_class_enrollment_session", columnList = "class_session_id")
    },
    uniqueConstraints = {
        @UniqueConstraint(columnNames = {"class_session_id", "user_id"})
    }
)
public class ClassEnrollment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "class_session_id", nullable = false)
    private ClassSession classSession;

    @Column(name = "class_session_id", insertable = false, updatable = false)
    private Long classSessionId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "user_id", insertable = false, updatable = false)
    private Long userId;

    @Column(name = "quoted_price_cents", nullable = false)
    private int quotedPriceCents = 0;

    @Column(nullable = false)
    private String status = "PENDING";

    @Column(name = "stripe_checkout_session_id", length = 255)
    private String stripeCheckoutSessionId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private ZonedDateTime createdAt = ZonedDateTime.now();
}
