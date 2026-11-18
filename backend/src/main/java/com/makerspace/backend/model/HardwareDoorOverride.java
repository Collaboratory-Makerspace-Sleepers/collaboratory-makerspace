package com.makerspace.backend.model;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.Instant;
import java.util.UUID;
@Entity @Table(name="hardware_door_overrides") @Getter @Setter
public class HardwareDoorOverride {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @Column(nullable=false) private Long userId;
    @Column(nullable=false) private UUID deviceId;
    @Column(nullable=false) private Long grantedBy;
    @Column(nullable=false, length=200) private String reason;
    @Column(nullable=false) private Instant createdAt = Instant.now();
    @Column(nullable=false) private Instant expiresAt;
    private Instant revokedAt;
    private Long revokedBy;
}
