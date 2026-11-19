package com.makerspace.backend.controller.dto;

import com.makerspace.backend.model.ClassSession;
import jakarta.persistence.EntityNotFoundException;

import java.time.ZonedDateTime;

public record ClassSessionDTO(
        Long id,
        String title,
        String description,
        Long instructorId,
        String instructorName,
        ZonedDateTime startTime,
        ZonedDateTime endTime,
        int maxCapacity,
        int priceCents,
        int memberPriceCents,
        String status,
        String imageUrl,
        String location,
        ZonedDateTime createdAt,
        long enrolledCount,
        long spotsRemaining,
        int resolvedPriceCents,
        String stripeProductId,
        String stripePriceId
) {
    public static ClassSessionDTO from(ClassSession s, long enrolledCount, int resolvedPriceCents) {
        String instructorName = null;
        if (s.getInstructor() != null) {
            try {
                var profile = s.getInstructor().getProfile();
                if (profile != null) {
                    instructorName = (profile.getFirstName() + " " + profile.getLastName()).trim();
                }
            } catch (EntityNotFoundException ignored) {
                // instructor was soft-deleted; show no name rather than crashing
            }
        }

        long spotsRemaining = s.getMaxCapacity() == 0
                ? -1   // -1 signals unlimited
                : Math.max(0, s.getMaxCapacity() - enrolledCount);

        return new ClassSessionDTO(
                s.getId(),
                s.getTitle(),
                s.getDescription(),
                s.getInstructorId(),
                instructorName,
                s.getStartTime(),
                s.getEndTime(),
                s.getMaxCapacity(),
                s.getPriceCents(),
                s.getMemberPriceCents(),
                s.getStatus(),
                s.getImageUrl(),
                s.getLocation(),
                s.getCreatedAt(),
                enrolledCount,
                spotsRemaining,
                resolvedPriceCents,
                s.getStripeProductId(),
                s.getStripePriceId()
        );
    }
}
