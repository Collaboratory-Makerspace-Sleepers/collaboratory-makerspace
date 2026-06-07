package com.makerspace.backend.controller.dto;

import com.makerspace.backend.model.Role;
import com.makerspace.backend.model.User;

import java.util.Set;

public record UserDTO(
        Long id,
        String email,
        String firstName,
        String lastName,
        Set<Role> roles
) {
    public static UserDTO from(User user) {
        return new UserDTO(
                user.getId(),
                user.getEmail(),
                user.getFirstName(),
                user.getLastName(),
                user.getRoles()
        );
    }
}