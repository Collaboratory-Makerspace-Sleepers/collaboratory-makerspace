package com.makerspace.backend.controller.dto;

import com.makerspace.backend.model.CancellationRequest;
import com.makerspace.backend.model.CancellationRequestStatus;
import com.makerspace.backend.model.PaymentRecord;

import java.time.ZonedDateTime;

public record CancellationRequestDTO(
        Long id,
        Long reservationId,
        String equipmentName,
        String userName,
        String userEmail,
        ZonedDateTime startTime,
        ZonedDateTime endTime,
        String reason,
        CancellationRequestStatus status,
        String adminNote,
        ZonedDateTime refundEligibleUntil,
        ZonedDateTime requestedAt,
        ZonedDateTime resolvedAt,
        String stripePaymentIntentId,
        int refundAmountCents
) {
    public static CancellationRequestDTO from(CancellationRequest cr, PaymentRecord pr) {
        String equipmentName = cr.getReservation().getEquipment() != null
                ? cr.getReservation().getEquipment().getName() : null;
        String userName = cr.getRequestedBy().getProfile() != null
                ? cr.getRequestedBy().getProfile().getFirstName() + " "
                  + cr.getRequestedBy().getProfile().getLastName()
                : cr.getRequestedBy().getEmail();
        String userEmail = cr.getRequestedBy().getEmail();

        return new CancellationRequestDTO(
                cr.getId(),
                cr.getReservation().getId(),
                equipmentName,
                userName,
                userEmail,
                cr.getReservation().getStartTime(),
                cr.getReservation().getEndTime(),
                cr.getReason(),
                cr.getStatus(),
                cr.getAdminNote(),
                cr.getRefundEligibleUntil(),
                cr.getRequestedAt(),
                cr.getResolvedAt(),
                pr != null ? pr.getStripePaymentIntentId() : null,
                pr != null ? pr.getAmountCents() : 0
        );
    }
}
