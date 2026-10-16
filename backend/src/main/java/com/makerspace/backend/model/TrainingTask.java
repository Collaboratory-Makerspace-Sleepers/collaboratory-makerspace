package com.makerspace.backend.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Getter
@Setter
@Table(
        name = "training_tasks",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_training_task_user_equipment",
                columnNames = {"user_id", "equipment_id"}
        ),
        indexes = @Index(name = "idx_training_tasks_user", columnList = "user_id, created_at")
)
public class TrainingTask {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "equipment_id", nullable = false)
    private Equipment equipment;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "video_completed_at")
    private LocalDateTime videoCompletedAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;
}