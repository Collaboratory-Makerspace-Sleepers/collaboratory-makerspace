package com.makerspace.backend.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "hardware_devices")
@Getter
@Setter
public class HardwareDevice {
    @Id private UUID id = UUID.randomUUID();
    @Column(nullable = false, length = 100) private String name;
    @Column(nullable = false, length = 100) private String area;
    @Column(name = "equipment_id") private Long equipmentId;
    @Column(nullable = false) private boolean enabled = true;
    @Column(name = "door_enabled", nullable = false) private boolean doorEnabled;
    @Column(name = "unlock_seconds", nullable = false) private int unlockSeconds = 5;
    @Column(name = "key_hash", length = 64) private String keyHash;
    @Column(name = "pairing_hash", length = 64) private String pairingHash;
    @Column(name = "pairing_expires_at") private Instant pairingExpiresAt;
    @Column(length = 100) private String hostname;
    @Column(name = "reader_status", length = 160) private String readerStatus;
    @Column(name = "last_seen_at") private Instant lastSeenAt;
    @Column(nullable = false) private boolean connected;
    @Column(name = "assignment_user_id") private Long assignmentUserId;
    @Column(name = "assignment_staff_id") private Long assignmentStaffId;
    @Column(name = "assignment_label", length = 100) private String assignmentLabel;
    @Column(name = "assignment_expires_at") private Instant assignmentExpiresAt;
    @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt = Instant.now();
    @Column(name = "deleted_at") private Instant deletedAt;
}
