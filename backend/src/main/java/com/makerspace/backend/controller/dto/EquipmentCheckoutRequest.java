package com.makerspace.backend.controller.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record EquipmentCheckoutRequest(
        @NotNull Long equipmentId,
        @NotNull String startTime,
        @NotNull String endTime,
        @Positive Double price
) {}
