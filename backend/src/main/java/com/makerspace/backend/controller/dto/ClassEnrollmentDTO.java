package com.makerspace.backend.controller.dto;

import com.makerspace.backend.model.ClassEnrollment;

import java.time.ZonedDateTime;

public record ClassEnrollmentDTO(
        Long id,
        Long classSessionId,
        Long userId,
        int quotedPriceCents,
        String status,
        String stripeCheckoutSessionId,
        ZonedDateTime createdAt
) {
    public static ClassEnrollmentDTO from(ClassEnrollment e) {
        return new ClassEnrollmentDTO(
                e.getId(),
                e.getClassSessionId(),
                e.getUserId(),
                e.getQuotedPriceCents(),
                e.getStatus(),
                e.getStripeCheckoutSessionId(),
                e.getCreatedAt()
        );
    }
}
