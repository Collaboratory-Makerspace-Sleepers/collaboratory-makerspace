package com.makerspace.backend.controller.dto;

import java.time.LocalDateTime;

public record UserActivityDTO(
        Long id,
        String name,
        String email,
        LocalDateTime createdAt,
        String membership,
        String allowed,
        boolean waiverSigned
) {}