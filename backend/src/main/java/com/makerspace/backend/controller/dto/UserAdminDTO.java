package com.makerspace.backend.controller.dto;

import com.makerspace.backend.model.Role;
import com.makerspace.backend.model.User;

import java.time.LocalDateTime;
import java.util.Set;

public record UserAdminDTO(
        Long id,
        String email,
        String firstName,
        String lastName,
        Set<Role> roles,
        LocalDateTime createdAt,
        LocalDateTime deletedAt
) {
    public static UserAdminDTO from(User user) {
        return new UserAdminDTO(
                user.getId(),
                user.getEmail(),
                user.getFirstName(),
                user.getLastName(),
                user.getRoles(),
                user.getCreatedAt(),
                user.getDeletedAt()
        );
    }
}