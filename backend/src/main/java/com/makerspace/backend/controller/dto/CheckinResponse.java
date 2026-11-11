package com.makerspace.backend.controller.dto;

public record CheckinResponse(
        boolean granted,
        String reason,
        String userName
) {
    public static CheckinResponse granted(String userName) {
        return new CheckinResponse(true, "Access granted", userName);
    }

    public static CheckinResponse denied(String reason) {
        return new CheckinResponse(false, reason, null);
    }
}
