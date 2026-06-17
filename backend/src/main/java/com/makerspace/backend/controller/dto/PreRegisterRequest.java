package com.makerspace.backend.controller.dto;

import com.makerspace.backend.model.Role;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

import java.util.Set;

public record PreRegisterRequest(
        @Email @NotBlank String email,
        @NotBlank String fullName,
        Set<Role> authorityRoles,   // INSTRUCTOR / STAFF / ADMIN only; null → empty set
        boolean sendInvite
) {}
