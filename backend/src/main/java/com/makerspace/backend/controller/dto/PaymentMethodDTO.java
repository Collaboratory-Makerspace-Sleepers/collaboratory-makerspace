package com.makerspace.backend.controller.dto;

public record PaymentMethodDTO(
        String id,
        String brand,
        String last4,
        int expMonth,
        int expYear,
        boolean isDefault
) {}
