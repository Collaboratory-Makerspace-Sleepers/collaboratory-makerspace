package com.makerspace.backend.controller.dto;

public record AccessCheckResponse(
        boolean granted,
        String reason,
        Long reservationId   // null when access is denied
) {
    public static AccessCheckResponse granted(Long reservationId) {
        return new AccessCheckResponse(true, "Access granted", reservationId);
    }

    public static AccessCheckResponse denied(String reason) {
        return new AccessCheckResponse(false, reason, null);
    }
}
