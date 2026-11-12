package com.makerspace.backend.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.ZonedDateTime;

@Entity
@Getter
@Setter
@Table(
    name = "subscription_audit_mismatch",
    indexes = {
        @Index(name = "idx_audit_mismatch_resolved", columnList = "resolved, audit_run_at DESC")
    }
)
public class SubscriptionAuditMismatch {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "audit_run_at", nullable = false)
    private ZonedDateTime auditRunAt;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "stripe_subscription_id", nullable = false, length = 255)
    private String stripeSubscriptionId;

    @Column(name = "field_name", nullable = false, length = 100)
    private String fieldName;

    @Column(name = "local_value", columnDefinition = "TEXT")
    private String localValue;

    @Column(name = "stripe_value", columnDefinition = "TEXT")
    private String stripeValue;

    @Column(name = "resolved", nullable = false)
    private boolean resolved = false;

    @Column(name = "resolved_at")
    private ZonedDateTime resolvedAt;
}
