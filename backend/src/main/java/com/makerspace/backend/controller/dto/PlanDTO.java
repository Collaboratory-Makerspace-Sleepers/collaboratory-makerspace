package com.makerspace.backend.controller.dto;

public record PlanDTO(
        String code,
        String displayName,
        int amountCents,
        String billingInterval
) {}