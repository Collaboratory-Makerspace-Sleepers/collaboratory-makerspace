package com.makerspace.backend.controller.dto;

import com.makerspace.backend.model.TrainingTask;

import java.time.LocalDateTime;

public record TrainingTaskDTO(
        Long id,
        Long equipmentId,
        String equipmentName,
        LocalDateTime createdAt,
        LocalDateTime videoCompletedAt,
        LocalDateTime completedAt,
        boolean videoCompleted,
        boolean completed
) {
    public static TrainingTaskDTO from(TrainingTask task) {
        return new TrainingTaskDTO(
                task.getId(),
                task.getEquipment().getId(),
                task.getEquipment().getName(),
                task.getCreatedAt(),
                task.getVideoCompletedAt(),
                task.getCompletedAt(),
                task.getVideoCompletedAt() != null,
                task.getCompletedAt() != null
        );
    }
}