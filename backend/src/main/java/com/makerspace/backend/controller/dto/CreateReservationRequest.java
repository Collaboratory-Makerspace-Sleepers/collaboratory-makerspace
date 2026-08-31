package com.makerspace.backend.controller.dto;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotNull;

import java.time.ZonedDateTime;

public record CreateReservationRequest(
        @NotNull Long equipmentId,
        @NotNull @Future ZonedDateTime startTime,
        @NotNull @Future ZonedDateTime endTime
) {}
