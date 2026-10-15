package com.makerspace.backend.controller.dto;

import java.time.LocalDateTime;
import java.util.List;

public record UserActivityDTO(
        Long id,
        String name,
        String email,
        LocalDateTime createdAt,
        String membership,
        String allowed,
        boolean waiverSigned,
        List<String> roles
) {}