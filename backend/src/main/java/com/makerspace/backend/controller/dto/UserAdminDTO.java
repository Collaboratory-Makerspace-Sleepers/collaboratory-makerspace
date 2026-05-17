package com.makerspace.backend.controller.dto;

import com.makerspace.backend.model.Role;
import com.makerspace.backend.model.User;

import java.time.LocalDateTime;

public record UserAdminDTO(
        Long id,
        String email,
        String firstName,
        String lastName,
        Role role,
        LocalDateTime createdAt,
        LocalDateTime deletedAt
) {
    public static UserAdminDTO from(User user) {
        return new UserAdminDTO(
                user.getId(),
                user.getEmail(),
                user.getFirstName(),
                user.getLastName(),
                user.getRole(),
                user.getCreatedAt(),
                user.getDeletedAt()
        );
    }
}