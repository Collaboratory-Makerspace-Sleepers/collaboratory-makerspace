package com.makerspace.backend.controller.dto;

import com.makerspace.backend.model.Role;
import com.makerspace.backend.model.User;

public record UserDTO(
        Long id,
        String email,
        String firstName,
        String lastName,
        Role role
) {
    public static UserDTO from(User user) {
        return new UserDTO(
                user.getId(),
                user.getEmail(),
                user.getFirstName(),
                user.getLastName(),
                user.getRole()
        );
    }
}