package com.makerspace.backend.controller.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

import java.util.Set;

public record PreRegisterRequest(
        @Email @NotBlank String email,
        @NotBlank String fullName,
        Set<String> roleCodes,   // role code strings (e.g. "INSTRUCTOR"); null → empty set
        boolean sendInvite
) {}
