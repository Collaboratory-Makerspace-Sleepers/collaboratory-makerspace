package com.makerspace.backend.controller.dto;

import com.makerspace.backend.model.MembershipStatus;

import java.time.ZonedDateTime;

public record MembershipDTO(
        MembershipStatus status,
        String planCode,
        String planName,
        int amountCents,
        String billingInterval,
        ZonedDateTime currentPeriodStart,
        ZonedDateTime currentPeriodEnd,
        boolean cancelAtPeriodEnd,
        ZonedDateTime gracePeriodEndsAt,
        ZonedDateTime createdAt,
        String stripeSubscriptionId
) {}
