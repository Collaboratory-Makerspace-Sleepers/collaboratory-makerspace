package com.makerspace.backend.controller.dto;

import jakarta.validation.constraints.NotBlank;

public record CheckinRequest(
        @NotBlank String cardId
) {}
