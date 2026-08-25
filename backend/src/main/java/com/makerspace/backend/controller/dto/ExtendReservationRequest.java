package com.makerspace.backend.controller.dto;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotNull;

import java.time.ZonedDateTime;

public record ExtendReservationRequest(
        @NotNull @Future ZonedDateTime newEndTime
) {}
