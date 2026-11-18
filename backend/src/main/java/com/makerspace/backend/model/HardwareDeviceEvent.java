package com.makerspace.backend.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "hardware_device_events")
@Getter
@Setter
public class HardwareDeviceEvent {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "device_id", nullable = false) private UUID deviceId;
    @Column(name = "event_id", nullable = false) private UUID eventId;
    @Column(nullable = false, length = 80) private String type;
    @Column(nullable = false, length = 400) private String detail;
    @Column(nullable = false, length = 10) private String outcome;
    @Column(nullable = false, length = 10) private String source;
    @Column(name = "card_uid", length = 32) private String cardUid;
    @Column(name = "scan_id") private Long scanId;
    @Column(name = "occurred_at", nullable = false) private Instant occurredAt;
    @Column(name = "received_at", nullable = false) private Instant receivedAt = Instant.now();
}
