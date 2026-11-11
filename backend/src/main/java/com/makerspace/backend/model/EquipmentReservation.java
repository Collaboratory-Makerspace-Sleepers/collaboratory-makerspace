package com.makerspace.backend.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.ZonedDateTime;

@Entity
@Getter
@Setter
@Table(
    name = "reservations",
    indexes = {
        @Index(name = "idx_reservations_user",            columnList = "user_id"),
        @Index(name = "idx_reservations_equipment",       columnList = "equipment_id"),
        @Index(name = "idx_reservations_active_overlap",  columnList = "equipment_id, start_time, end_time")
    }
)
public class EquipmentReservation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "user_id", insertable = false, updatable = false)
    private Long userId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "equipment_id", nullable = false)
    private Equipment equipment;

    @Column(name = "equipment_id", insertable = false, updatable = false)
    private Long equipmentId;

    @Column(name = "start_time", nullable = false)
    private ZonedDateTime startTime;

    @Column(name = "end_time", nullable = false)
    private ZonedDateTime endTime;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private ReservationStatus status = ReservationStatus.ACTIVE;

    @Column(name = "cancelled_at")
    private ZonedDateTime cancelledAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private ZonedDateTime createdAt = ZonedDateTime.now();

    /**
     * The per-hour rate in cents that applied to this booking at the time it was created.
     * Snapshotted so price changes do not retroactively affect existing reservations.
     */
    @Column(name = "quoted_rate_cents", nullable = false)
    private int quotedRateCents = 0;

    /**
     * Estimated total cost in cents based on the duration and quoted rate.
     * Actual charge may differ if the reservation is extended or shortened.
     */
    @Column(name = "estimated_cost_cents", nullable = false)
    private int estimatedCostCents = 0;
}
