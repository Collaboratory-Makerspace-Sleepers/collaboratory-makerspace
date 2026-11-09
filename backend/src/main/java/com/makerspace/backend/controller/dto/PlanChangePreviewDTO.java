package com.makerspace.backend.controller.dto;

import java.time.ZonedDateTime;

public record PlanChangePreviewDTO(
        String direction,           // "UPGRADE" or "DOWNGRADE"
        int proratedAmountCents,    // charge today; 0 for downgrade
        String currency,
        ZonedDateTime effectiveDate // null for upgrade (immediate); currentPeriodEnd for downgrade
) {}
