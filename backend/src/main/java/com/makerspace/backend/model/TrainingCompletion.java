package com.makerspace.backend.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.ZonedDateTime;

@Entity
@Getter
@Setter
@Table(
    name = "training_completion",
    uniqueConstraints = @UniqueConstraint(
        name = "uq_training_user_equipment",
        columnNames = {"user_id", "equipment_id"}
    )
)
public class TrainingCompletion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "equipment_id", nullable = false)
    private Equipment equipment;

    @Column(name = "completed_at", nullable = false)
    private ZonedDateTime completedAt;

    /**
     * The reservation used for the training session, if booked through the app.
     * Null when an admin manually records a completion (e.g. prior training elsewhere).
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "training_reservation_id")
    private EquipmentReservation trainingReservation;

    /** Admin who recorded this completion. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "verified_by")
    private User verifiedBy;

    @Column
    private String notes;
}
