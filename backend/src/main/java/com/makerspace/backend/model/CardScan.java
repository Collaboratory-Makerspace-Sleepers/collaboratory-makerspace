package com.makerspace.backend.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

/** One tap of a card on a reader, whether or not the card is known. */
@Entity
@Table(name = "card_scans")
@Getter
@Setter
public class CardScan {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "card_uid", nullable = false, length = 32)
    private String cardUid;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "user_id")
    private User user;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "equipment_id")
    private Equipment equipment;

    @Column(name = "device_id", length = 100)
    private String deviceId;

    @Column(nullable = false)
    private boolean approved;

    @Column(length = 255)
    private String reason;

    @Column(name = "scanned_at", nullable = false)
    private Instant scannedAt = Instant.now();
}
