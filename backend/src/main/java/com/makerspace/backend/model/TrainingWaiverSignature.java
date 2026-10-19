package com.makerspace.backend.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.time.ZonedDateTime;

@Entity
@Getter
@Setter
@Table(name = "training_waiver_signatures")
public class TrainingWaiverSignature {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "equipment_id", nullable = false)
    private Equipment equipment;

    @Column(name = "signer_name", nullable = false, length = 200)
    private String signerName;

    @Column(name = "signed_at", nullable = false)
    private LocalDateTime signedAt;

    /**
     * The reservation this waiver was signed for. Null until the booking is created —
     * the user signs before the reservation exists, then ReservationService links it.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reservation_id")
    private EquipmentReservation reservation;

    @Column(name = "reservation_id", insertable = false, updatable = false)
    private Long reservationId;

    /**
     * The waiver version in effect at the time of signing. If equipment.currentWaiverVersion
     * has since been incremented, this signature is stale and cannot be used to book.
     */
    @Column(name = "waiver_version", nullable = false)
    private int waiverVersion = 1;

    /** The intended reservation start time captured at signing. */
    @Column(name = "start_time", nullable = false)
    private ZonedDateTime startTime;

    /** The intended reservation end time captured at signing. */
    @Column(name = "end_time", nullable = false)
    private ZonedDateTime endTime;
}
