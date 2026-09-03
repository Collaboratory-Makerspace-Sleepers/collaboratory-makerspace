package com.makerspace.backend.controller.dto;

import jakarta.validation.constraints.NotNull;

import java.util.Set;

/** Replaces the full permission set for a role. */
public record UpdatePermissionsRequest(
        @NotNull Set<String> permissions
) {}
