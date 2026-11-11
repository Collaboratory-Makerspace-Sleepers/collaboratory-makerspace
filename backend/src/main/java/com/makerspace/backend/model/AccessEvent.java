package com.makerspace.backend.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.ZonedDateTime;

@Entity
@Getter
@Setter
@Table(
    name = "access_events",
    indexes = {
        @Index(name = "idx_access_events_user", columnList = "user_id"),
        @Index(name = "idx_access_events_time", columnList = "occurred_at")
    }
)
public class AccessEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Null when the card ID is not registered to any account. */
    @Column(name = "user_id")
    private Long userId;

    @Column(name = "card_id", nullable = false)
    private String cardId;

    @Column(name = "granted", nullable = false)
    private boolean granted;

    @Column(name = "denial_reason")
    private String denialReason;

    @Column(name = "occurred_at", nullable = false, updatable = false)
    private ZonedDateTime occurredAt = ZonedDateTime.now();
}
