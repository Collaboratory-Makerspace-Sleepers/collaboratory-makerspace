package com.makerspace.backend.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.ZonedDateTime;

@Entity
@Getter
@Setter
@Table(
    name = "studio_assignment",
    uniqueConstraints = {
        @UniqueConstraint(name = "uq_studio_user",      columnNames = "user_id"),
        @UniqueConstraint(name = "uq_studio_equipment", columnNames = "equipment_id")
    }
)
public class StudioAssignment {

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

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "membership_id", nullable = false)
    private Membership membership;

    @Column(name = "membership_id", insertable = false, updatable = false)
    private Long membershipId;

    @Column(name = "assigned_at", nullable = false)
    private ZonedDateTime assignedAt = ZonedDateTime.now();
}
