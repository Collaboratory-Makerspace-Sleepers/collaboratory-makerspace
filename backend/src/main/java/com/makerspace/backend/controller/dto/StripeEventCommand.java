package com.makerspace.backend.controller.dto;

import java.time.ZonedDateTime;

public record StripeEventCommand(
        String eventId,
        String type,
        String apiVersion,
        boolean livemode,
        ZonedDateTime occurredAt,
        String source,   // EVENTBRIDGE | RECONCILIATION | MANUAL
        String payload   // raw Stripe event JSON
) {}
