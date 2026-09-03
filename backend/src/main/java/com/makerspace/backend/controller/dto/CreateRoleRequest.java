package com.makerspace.backend.controller.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

import java.util.Set;

public record CreateRoleRequest(
        @NotBlank
        @Pattern(regexp = "[A-Z0-9_]{1,30}", message = "Role code must be uppercase alphanumeric with underscores, max 30 chars")
        String code,
        @NotBlank String description,
        Set<String> permissions   // optional initial permission set; null → empty
) {}
