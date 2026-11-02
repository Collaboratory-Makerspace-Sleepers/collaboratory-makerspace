package com.makerspace.backend.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.ZonedDateTime;

@Entity
@Getter
@Setter
@Table(
    name = "cancellation_requests",
    indexes = {
        @Index(name = "idx_cr_reservation", columnList = "reservation_id"),
        @Index(name = "idx_cr_status",      columnList = "status")
    }
)
public class CancellationRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reservation_id", nullable = false)
    private EquipmentReservation reservation;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "requested_by_user_id", nullable = false)
    private User requestedBy;

    @Column(columnDefinition = "text")
    private String reason;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private CancellationRequestStatus status = CancellationRequestStatus.PENDING;

    @Column(name = "admin_note", columnDefinition = "text")
    private String adminNote;

    @Column(name = "refund_eligible_until", nullable = false)
    private ZonedDateTime refundEligibleUntil;

    @Column(name = "requested_at", nullable = false)
    private ZonedDateTime requestedAt = ZonedDateTime.now();

    @Column(name = "resolved_at")
    private ZonedDateTime resolvedAt;
}
