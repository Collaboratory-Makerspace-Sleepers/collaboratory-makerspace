package com.makerspace.backend.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Getter
@Setter
@Table(
        name = "training_waiver_signatures",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_training_waiver_user_equipment",
                columnNames = {"user_id", "equipment_id"}
        )
)
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
}