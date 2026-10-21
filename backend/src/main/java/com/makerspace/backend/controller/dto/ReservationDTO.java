package com.makerspace.backend.controller.dto;

import com.makerspace.backend.model.EquipmentReservation;
import com.makerspace.backend.model.ReservationStatus;

import java.time.ZonedDateTime;

public record ReservationDTO(
        Long id,
        Long userId,
        Long equipmentId,
        String equipmentName,
        String equipmentCategory,
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
                r.getEquipment().getCategory(),
                r.getStartTime(),
                r.getEndTime(),
                effectiveStatus(r),
                r.getCreatedAt(),
                r.getCancelledAt()
        );
    }

    /** A booking whose end time has passed is shown as COMPLETED so the slot reads as finished. */
    private static ReservationStatus effectiveStatus(EquipmentReservation r) {
        if (r.getStatus() == ReservationStatus.ACTIVE && r.getEndTime() != null
                && r.getEndTime().isBefore(ZonedDateTime.now())) {
            return ReservationStatus.COMPLETED;
        }
        return r.getStatus();
    }
}
