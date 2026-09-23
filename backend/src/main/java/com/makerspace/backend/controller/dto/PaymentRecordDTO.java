package com.makerspace.backend.controller.dto;

import com.makerspace.backend.model.PaymentKind;
import com.makerspace.backend.model.PaymentStatus;

import java.time.ZonedDateTime;

public record PaymentRecordDTO(
        PaymentKind kind,
        PaymentStatus status,
        int amountCents,
        String currency,
        String description,
        ZonedDateTime occurredAt
) {
}
