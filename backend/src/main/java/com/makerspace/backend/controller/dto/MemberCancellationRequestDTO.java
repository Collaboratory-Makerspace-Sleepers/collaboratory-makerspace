package com.makerspace.backend.controller.dto;

import com.makerspace.backend.model.CancellationRequestStatus;
import com.makerspace.backend.model.ReservationStatus;

import java.time.ZonedDateTime;

public record MemberCancellationRequestDTO(
        Long id,
        Long reservationId,
        String equipmentName,
        ZonedDateTime startTime,
        ReservationStatus reservationStatus,
        CancellationRequestStatus status,
        String rejectionReason,
        ZonedDateTime requestedAt,
        ZonedDateTime resolvedAt
) {}
