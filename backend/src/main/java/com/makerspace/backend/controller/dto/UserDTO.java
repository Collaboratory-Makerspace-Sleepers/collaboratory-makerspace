package com.makerspace.backend.controller.dto;

import com.makerspace.backend.model.Role;
import com.makerspace.backend.model.User;
import com.makerspace.backend.model.UserProfile;

import java.util.Set;

public record UserDTO(
        Long id,
        String email,
        String firstName,
        String lastName,
        Set<Role> roles
) {
    public static UserDTO from(User user) {
        UserProfile profile = user.getProfile();
        return new UserDTO(
                user.getId(),
                user.getEmail(),
                profile != null ? profile.getFirstName() : null,
                profile != null ? profile.getLastName() : null,
                user.getRoles()
        );
    }
}