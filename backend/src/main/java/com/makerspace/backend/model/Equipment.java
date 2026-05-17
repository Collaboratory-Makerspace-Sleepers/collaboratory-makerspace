package com.makerspace.backend.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Getter
@Setter
@Table(name = "equipment")
public class Equipment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    private String description;

    private String category;

    private String imageUrl;

    @Enumerated(EnumType.STRING)
    private EquipmentStatus status = EquipmentStatus.AVAILABLE;

    @Column(name = "created_at")
    private LocalDateTime createdAt = LocalDateTime.now();

}

