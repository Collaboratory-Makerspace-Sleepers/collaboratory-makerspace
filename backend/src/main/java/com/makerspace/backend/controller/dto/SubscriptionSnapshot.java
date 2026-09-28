package com.makerspace.backend.controller.dto;

import com.makerspace.backend.model.MembershipStatus;
import com.makerspace.backend.model.User;

import java.time.ZonedDateTime;

public record SubscriptionSnapshot(
        User user,
        String subscriptionId,
        String stripePriceId,
        MembershipStatus status,
        ZonedDateTime currentPeriodStart,
        ZonedDateTime currentPeriodEnd,
        boolean cancelAtPeriodEnd,
        ZonedDateTime canceledAt,
        ZonedDateTime updatedAt
) {}
