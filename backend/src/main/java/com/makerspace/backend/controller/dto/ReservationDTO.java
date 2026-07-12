package com.makerspace.backend.controller.dto;

import com.makerspace.backend.model.EquipmentReservation;
import com.makerspace.backend.model.ReservationStatus;

import java.time.ZonedDateTime;

public record ReservationDTO(
        Long id,
        Long userId,
        Long equipmentId,
        String equipmentName,
        ZonedDateTime startTime,
        ZonedDateTime endTime,
        ReservationStatus status,
        ZonedDateTime createdAt,
        ZonedDateTime cancelledAt
) {
    public static ReservationDTO from(EquipmentReservation r) {
        return new ReservationDTO(
                r.getId(),
                r.getUserId(),
                r.getEquipmentId(),
                r.getEquipment().getName(),
                r.getStartTime(),
                r.getEndTime(),
                r.getStatus(),
                r.getCreatedAt(),
                r.getCancelledAt()
        );
    }
}