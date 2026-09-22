package com.makerspace.backend.controller.dto;

public record CurrentMembershipDTO(
        String planCode,
        String planName,
        int amountCents,
        boolean hasActiveMembership
) {}