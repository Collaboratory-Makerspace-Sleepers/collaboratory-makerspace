package com.makerspace.backend.controller.dto;

import jakarta.validation.constraints.NotNull;

public record AccessCheckRequest(
        @NotNull Long userId,
        @NotNull Long equipmentId
) {}
