package com.makerspace.backend.controller.dto;

import com.makerspace.backend.model.AppRole;
import com.makerspace.backend.model.User;
import com.makerspace.backend.model.UserProfile;

import java.time.LocalDateTime;
import java.util.Set;
import java.util.stream.Collectors;

public record UserAdminDTO(
        Long id,
        String email,
        String firstName,
        String lastName,
        Set<String> roles,
        LocalDateTime createdAt,
        LocalDateTime deletedAt
) {
    public static UserAdminDTO from(User user) {
        UserProfile profile = user.getProfile();
        return new UserAdminDTO(
                user.getId(),
                user.getEmail(),
                profile != null ? profile.getFirstName() : null,
                profile != null ? profile.getLastName() : null,
                user.getRoles().stream().map(AppRole::getCode).collect(Collectors.toSet()),
                user.getCreatedAt(),
                user.getDeletedAt()
        );
    }
}
