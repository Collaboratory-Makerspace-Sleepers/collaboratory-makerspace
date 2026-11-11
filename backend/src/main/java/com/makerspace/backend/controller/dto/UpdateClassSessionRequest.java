package com.makerspace.backend.controller.dto;

import java.time.ZonedDateTime;

public record UpdateClassSessionRequest(
        String title,
        String description,
        Long instructorId,
        ZonedDateTime startTime,
        ZonedDateTime endTime,
        int maxCapacity,
        int priceCents,
        int memberPriceCents,
        String imageUrl,
        String location,
        String status
) {}
